package org.whispersystems.textsecuregcm.auth;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import org.whispersystems.textsecuregcm.util.Util;

import java.util.Optional;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;

public class BaseDiskuvAccountAuthenticator {
  private final MetricRegistry metricRegistry               = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          authenticationFailedMeter    = metricRegistry.meter(name(getClass(), "authentication", "failed"         ));
  private final Meter          authenticationSucceededMeter = metricRegistry.meter(name(getClass(), "authentication", "succeeded"      ));
  private final Meter          noSuchAccountMeter           = metricRegistry.meter(name(getClass(), "authentication", "noSuchAccount"  ));
  private final Meter          noSuchDeviceMeter            = metricRegistry.meter(name(getClass(), "authentication", "noSuchDevice"   ));
  private final Meter          accountDisabledMeter         = metricRegistry.meter(name(getClass(), "authentication", "accountDisabled"));
  private final Meter          deviceDisabledMeter          = metricRegistry.meter(name(getClass(), "authentication", "deviceDisabled" ));
  private final Meter          invalidJwtTokenMeter         = metricRegistry.meter(name(getClass(), "authentication", "invalidJwtToken"));
  private final Meter          invalidAuthHeaderMeter       = metricRegistry.meter(name(getClass(), "authentication", "invalidHeader"  ));

  private final AccountsManager accountsManager;
  private final JwtAuthentication jwtAuthentication;

  public BaseDiskuvAccountAuthenticator(
      AccountsManager accountsManager, JwtAuthentication jwtAuthentication) {
    this.accountsManager = accountsManager;
    this.jwtAuthentication = jwtAuthentication;
  }

  public Optional<Account> authenticate(DiskuvCredentials credentials, boolean enabledRequired) {
    final UUID accountId;
    try {
      String emailAddress = jwtAuthentication.verifyBearerTokenAndGetEmailAddress(credentials.getBearerToken());
      accountId = DiskuvUuidUtil.uuidForEmailAddress(emailAddress);
    } catch (IllegalArgumentException iae) {
      invalidJwtTokenMeter.mark();
      return Optional.empty();
    }
    try {
      Optional<Account> account = accountsManager.get(accountId);

      if (!account.isPresent()) {
        noSuchAccountMeter.mark();
        return Optional.empty();
      }

      Optional<Device> device = account.get().getDevice(credentials.getDeviceId());

      if (!device.isPresent()) {
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

  private void updateLastSeen(Account account, Device device) {
    if (device.getLastSeen() != Util.todayInMillis()) {
      device.setLastSeen(Util.todayInMillis());
      accountsManager.update(account);
    }
  }
}