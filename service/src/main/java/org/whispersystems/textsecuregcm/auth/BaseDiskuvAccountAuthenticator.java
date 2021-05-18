package org.whispersystems.textsecuregcm.auth;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.diskuv.communicatorservice.auth.DiskuvDeviceCredentials;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.basic.BasicCredentials;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.DiskuvUuidType;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import org.whispersystems.textsecuregcm.util.Util;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.codahale.metrics.MetricRegistry.name;

public class BaseDiskuvAccountAuthenticator {
  private final MetricRegistry metricRegistry =
      SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter authenticationFailedMeter =
      metricRegistry.meter(name(getClass(), "authentication", "failed"));
  private final Meter authenticationSucceededMeter =
      metricRegistry.meter(name(getClass(), "authentication", "succeeded"));
  private final Meter noSuchAccountMeter =
      metricRegistry.meter(name(getClass(), "authentication", "noSuchAccount"));
  private final Meter noSuchDeviceMeter =
      metricRegistry.meter(name(getClass(), "authentication", "noSuchDevice"));
  private final Meter accountDisabledMeter =
      metricRegistry.meter(name(getClass(), "authentication", "accountDisabled"));
  private final Meter deviceDisabledMeter =
      metricRegistry.meter(name(getClass(), "authentication", "deviceDisabled"));
  private final Meter invalidJwtTokenMeter =
      metricRegistry.meter(name(getClass(), "authentication", "invalidJwtToken"));
  private final Meter invalidAccountUuidMeter         =
          metricRegistry.meter(name(getClass(), "authentication", "invalidAccountUuid"));
  private final Meter illegalOutdoorsAccountUuidMeter =
          metricRegistry.meter(name(getClass(), "authentication", "illegalOutdoorsAccountUuid"));
  private final Meter illegalSanctuaryAccountUuidMeter =
          metricRegistry.meter(name(getClass(), "authentication", "illegalSanctuaryAccountUuid"));
  private final Meter invalidAuthHeaderMeter          =
      metricRegistry.meter(name(getClass(), "authentication", "invalidHeader"));

  private final String daysSinceLastSeenDistributionName = name(getClass(), "authentication", "daysSinceLastSeen");

  private static final String IS_PRIMARY_DEVICE_TAG = "isPrimary";

  private final Logger logger = LoggerFactory.getLogger(BaseDiskuvAccountAuthenticator.class);

  private final AccountsManager accountsManager;
  private final JwtAuthentication jwtAuthentication;
  private final Clock           clock;

  public BaseDiskuvAccountAuthenticator(
      AccountsManager accountsManager, JwtAuthentication jwtAuthentication) {
    this(accountsManager, jwtAuthentication, Clock.systemUTC());
  }

  @VisibleForTesting
  public BaseDiskuvAccountAuthenticator(AccountsManager accountsManager, JwtAuthentication jwtAuthentication, Clock clock) {
    this.accountsManager = accountsManager;
    this.clock           = clock;
    this.jwtAuthentication = jwtAuthentication;
  }

  public Optional<Account> authenticate(DiskuvDeviceCredentials credentials, boolean enabledRequired) {
    final java.util.UUID authenticatedOutdoorsUuid;
    try {
      String emailAddress = jwtAuthentication.verifyBearerTokenAndGetEmailAddress(credentials.getBearerToken());
      authenticatedOutdoorsUuid = DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress);
    } catch (IllegalArgumentException iae) {
      invalidJwtTokenMeter.mark();
      return Optional.empty();
    }

    final java.util.UUID accountUuid = credentials.getAccountUuid();
    final DiskuvUuidType diskuvUuidType;
    try {
      diskuvUuidType = DiskuvUuidUtil.verifyDiskuvUuid(accountUuid.toString());
    } catch (IllegalArgumentException iae) {
      invalidAccountUuidMeter.mark();
      return Optional.empty();
    }

    // validate UUID if Outdoors (which anybody with knowledge of the email address can reconstruct)
    if (diskuvUuidType == DiskuvUuidType.OUTDOORS && !authenticatedOutdoorsUuid.equals(accountUuid)) {
      illegalOutdoorsAccountUuidMeter.mark();
      return Optional.empty();
    }

    try {
      Optional<Account> account = accountsManager.get(accountUuid);

      if (account.isEmpty()) {
        noSuchAccountMeter.mark();
        return Optional.empty();
      }

      // validate UUID if Sanctuary
      if (diskuvUuidType == DiskuvUuidType.SANCTUARY_SPECIFIC) {
        if (!authenticatedOutdoorsUuid.toString().equals(account.get().getPin())) {
          illegalSanctuaryAccountUuidMeter.mark();
          return Optional.empty();
        }
      }

      Optional<Device> device = account.get().getDevice(credentials.getDeviceId());

      if (device.isEmpty()) {
        noSuchDeviceMeter.mark();
        return Optional.empty();
      }

      if (enabledRequired) {
        if (!device.get().isEnabled()) {
          deviceDisabledMeter.mark();
          return Optional.empty();
        }

        if (!account.get().isEnabled()) {
          accountDisabledMeter.mark();
          return Optional.empty();
        }
      }

      if (device.get().getAuthenticationCredentials().verify(credentials.getDevicePassword())) {
        authenticationSucceededMeter.mark();
        account.get().setAuthenticatedDevice(device.get());
        account.get().setAuthenticatedOutdoorsUuid(authenticatedOutdoorsUuid);
        updateLastSeen(account.get(), device.get());
        return account;
      }

      authenticationFailedMeter.mark();
      return Optional.empty();
    } catch (IllegalArgumentException iae) {
      invalidAuthHeaderMeter.mark();
      return Optional.empty();
    }
  }

  @VisibleForTesting
  public void updateLastSeen(Account account, Device device) {
    final long lastSeenOffsetSeconds   = Math.abs(account.getUuid().getLeastSignificantBits()) % ChronoUnit.DAYS.getDuration().toSeconds();
    final long todayInMillisWithOffset = Util.todayInMillisGivenOffsetFromNow(clock, Duration.ofSeconds(lastSeenOffsetSeconds).negated());

    if (device.getLastSeen() < todayInMillisWithOffset) {
      DistributionSummary.builder(daysSinceLastSeenDistributionName)
          .tags(IS_PRIMARY_DEVICE_TAG, String.valueOf(device.isMaster()))
          .publishPercentileHistogram()
          .register(Metrics.globalRegistry)
          .record(Duration.ofMillis(todayInMillisWithOffset - device.getLastSeen()).toDays());

      device.setLastSeen(Util.todayInMillis(clock));
      accountsManager.update(account);
    }
  }
}
