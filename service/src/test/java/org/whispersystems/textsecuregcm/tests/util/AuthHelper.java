package org.whispersystems.textsecuregcm.tests.util;

import com.google.common.collect.ImmutableMap;
import org.mockito.ArgumentMatcher;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.auth.DiskuvAccountAuthenticator;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedDiskuvAccountAuthenticator;
import com.diskuv.communicatorservice.auth.DiskuvDeviceCredentialAuthFilter;
import com.diskuv.communicatorservice.auth.DiskuvDeviceCredentials;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.PolymorphicAuthDynamicFeature;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthHelper {
  public static final String VALID_BEARER_TOKEN = "blah";
  public static final String VALID_EMAIL        = "email@test.com";
  public static final long   VALID_DEVICE_ID    = 1L;
  public static final String VALID_DEVICE_ID_STRING = Long.toString(VALID_DEVICE_ID);
  public static final String VALID_NUMBER       = "+14150000000";
  public static final UUID   VALID_UUID         = DiskuvUuidUtil.uuidForEmailAddress(VALID_EMAIL);
  public static final String VALID_PASSWORD     = "foo";

  public static final String VALID_BEARER_TOKEN_TWO     = "bliss";
  public static final String VALID_EMAIL_TWO            = "email2@test.com";
  public static final long   VALID_DEVICE_ID_TWO        = 1L;
  public static final String VALID_DEVICE_ID_STRING_TWO = Long.toString(VALID_DEVICE_ID_TWO);
  public static final String VALID_NUMBER_TWO = "+201511111110";
  public static final UUID   VALID_UUID_TWO    = DiskuvUuidUtil.uuidForEmailAddress(VALID_EMAIL_TWO);
  public static final String VALID_PASSWORD_TWO = "baz";

  public static final String INVALID_BEARER_TOKEN = "bleak";
  public static final long   INVALID_DEVICE_ID    = 1L;
  public static final String INVALID_DEVICE_ID_STRING = Long.toString(INVALID_DEVICE_ID);
  public static final UUID   INVALID_UUID     = UUID.randomUUID();
  public static final String INVALID_PASSWORD = "bar";

  public static final String DISABLED_BEARER_TOKEN     = "bat";
  public static final long   DISABLED_DEVICE_ID        = 1L;
  public static final String DISABLED_DEVICE_ID_STRING = Long.toString(DISABLED_DEVICE_ID);
  public static final String DISABLED_EMAIL            = "disbled@test.com";
  public static final String DISABLED_NUMBER           = "+78888888";
  public static final UUID   DISABLED_UUID             = DiskuvUuidUtil.uuidForEmailAddress(DISABLED_EMAIL);
  public static final String DISABLED_PASSWORD         = "poof";

  public static final String VALID_IDENTITY = "BcxxDU9FGMda70E7+Uvm7pnQcEdXQ64aJCpPUeRSfcFo";
  public static JwtAuthentication JWT_AUTHENTICATION = mock(JwtAuthentication.class);

  public static AccountsManager ACCOUNTS_MANAGER  = mock(AccountsManager.class);
  public static Account         VALID_ACCOUNT     = mock(Account.class        );
  public static Account         VALID_ACCOUNT_TWO = mock(Account.class        );
  public static Account         DISABLED_ACCOUNT  = mock(Account.class        );

  public static Device VALID_DEVICE     = mock(Device.class);
  public static Device VALID_DEVICE_TWO = mock(Device.class);
  public static Device DISABLED_DEVICE  = mock(Device.class);

  private static AuthenticationCredentials VALID_CREDENTIALS     = mock(AuthenticationCredentials.class);
  private static AuthenticationCredentials VALID_CREDENTIALS_TWO = mock(AuthenticationCredentials.class);
  private static AuthenticationCredentials DISABLED_CREDENTIALS  = mock(AuthenticationCredentials.class);

  public static PolymorphicAuthDynamicFeature getAuthFilter() {
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(VALID_BEARER_TOKEN)).thenReturn(VALID_EMAIL);
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(VALID_BEARER_TOKEN_TWO)).thenReturn(VALID_EMAIL_TWO);
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(DISABLED_BEARER_TOKEN)).thenReturn(DISABLED_EMAIL);

    when(VALID_CREDENTIALS.verify("foo".getBytes(StandardCharsets.UTF_8))).thenReturn(true);
    when(VALID_CREDENTIALS_TWO.verify("baz".getBytes(StandardCharsets.UTF_8))).thenReturn(true);
    when(DISABLED_CREDENTIALS.verify(DISABLED_PASSWORD.getBytes(StandardCharsets.UTF_8))).thenReturn(true);

    when(VALID_DEVICE.getAuthenticationCredentials()).thenReturn(VALID_CREDENTIALS);
    when(VALID_DEVICE_TWO.getAuthenticationCredentials()).thenReturn(VALID_CREDENTIALS_TWO);
    when(DISABLED_DEVICE.getAuthenticationCredentials()).thenReturn(DISABLED_CREDENTIALS);

    when(VALID_DEVICE.isMaster()).thenReturn(true);
    when(VALID_DEVICE_TWO.isMaster()).thenReturn(true);
    when(DISABLED_DEVICE.isMaster()).thenReturn(true);

    when(VALID_DEVICE.getId()).thenReturn(VALID_DEVICE_ID);
    when(VALID_DEVICE_TWO.getId()).thenReturn(VALID_DEVICE_ID);
    when(DISABLED_DEVICE.getId()).thenReturn(VALID_DEVICE_ID);

    when(VALID_DEVICE.isEnabled()).thenReturn(true);
    when(VALID_DEVICE_TWO.isEnabled()).thenReturn(true);
    when(DISABLED_DEVICE.isEnabled()).thenReturn(false);

    when(VALID_ACCOUNT.getDevice(VALID_DEVICE_ID)).thenReturn(Optional.of(VALID_DEVICE));
    when(VALID_ACCOUNT_TWO.getDevice(eq(VALID_DEVICE_ID_TWO))).thenReturn(Optional.of(VALID_DEVICE_TWO));
    when(DISABLED_ACCOUNT.getDevice(eq(DISABLED_DEVICE_ID))).thenReturn(Optional.of(DISABLED_DEVICE));

    when(VALID_ACCOUNT_TWO.getEnabledDeviceCount()).thenReturn(6);

    when(VALID_ACCOUNT.getNumber()).thenReturn(VALID_NUMBER);
    when(VALID_ACCOUNT.getUuid()).thenReturn(VALID_UUID);
    when(VALID_ACCOUNT_TWO.getNumber()).thenReturn(VALID_NUMBER_TWO);
    when(VALID_ACCOUNT_TWO.getUuid()).thenReturn(VALID_UUID_TWO);
    when(DISABLED_ACCOUNT.getNumber()).thenReturn(DISABLED_NUMBER);
    when(DISABLED_ACCOUNT.getUuid()).thenReturn(DISABLED_UUID);

    when(VALID_ACCOUNT.getAuthenticatedDevice()).thenReturn(Optional.of(VALID_DEVICE));
    when(VALID_ACCOUNT_TWO.getAuthenticatedDevice()).thenReturn(Optional.of(VALID_DEVICE_TWO));
    when(DISABLED_ACCOUNT.getAuthenticatedDevice()).thenReturn(Optional.of(DISABLED_DEVICE));

    when(VALID_ACCOUNT.getRelay()).thenReturn(Optional.<String>empty());
    when(VALID_ACCOUNT_TWO.getRelay()).thenReturn(Optional.<String>empty());

    when(VALID_ACCOUNT.isEnabled()).thenReturn(true);
    when(VALID_ACCOUNT_TWO.isEnabled()).thenReturn(true);
    when(DISABLED_ACCOUNT.isEnabled()).thenReturn(false);

    when(VALID_ACCOUNT.getIdentityKey()).thenReturn(VALID_IDENTITY);

    when(ACCOUNTS_MANAGER.get(VALID_NUMBER)).thenReturn(Optional.of(VALID_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(VALID_UUID)).thenReturn(Optional.of(VALID_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(VALID_NUMBER)))).thenReturn(Optional.of(VALID_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(VALID_UUID)))).thenReturn(Optional.of(VALID_ACCOUNT));

    when(ACCOUNTS_MANAGER.get(VALID_NUMBER_TWO)).thenReturn(Optional.of(VALID_ACCOUNT_TWO));
    when(ACCOUNTS_MANAGER.get(VALID_UUID_TWO)).thenReturn(Optional.of(VALID_ACCOUNT_TWO));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(VALID_NUMBER_TWO)))).thenReturn(Optional.of(VALID_ACCOUNT_TWO));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(VALID_UUID_TWO)))).thenReturn(Optional.of(VALID_ACCOUNT_TWO));

    when(ACCOUNTS_MANAGER.get(DISABLED_NUMBER)).thenReturn(Optional.of(DISABLED_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(DISABLED_UUID)).thenReturn(Optional.of(DISABLED_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(DISABLED_NUMBER)))).thenReturn(Optional.of(DISABLED_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(DISABLED_UUID)))).thenReturn(Optional.of(DISABLED_ACCOUNT));

    AuthFilter<DiskuvDeviceCredentials, Account>                  accountAuthFilter                  = new DiskuvDeviceCredentialAuthFilter.Builder<Account>().setAuthenticator(new DiskuvAccountAuthenticator(ACCOUNTS_MANAGER, JWT_AUTHENTICATION)).buildAuthFilter();
    AuthFilter<DiskuvDeviceCredentials, DisabledPermittedAccount> disabledPermittedAccountAuthFilter = new DiskuvDeviceCredentialAuthFilter.Builder<DisabledPermittedAccount>().setAuthenticator(new DisabledPermittedDiskuvAccountAuthenticator(ACCOUNTS_MANAGER, JWT_AUTHENTICATION)).buildAuthFilter();

    return new PolymorphicAuthDynamicFeature<>(ImmutableMap.of(Account.class, accountAuthFilter,
                                                               DisabledPermittedAccount.class, disabledPermittedAccountAuthFilter));
  }

  public static String getAuthHeader(String number, String password) {
    String encodedPassword = Base64.encodeBytes(password.getBytes(StandardCharsets.UTF_8));
    return "Basic " + Base64.encodeBytes((number + ":" + encodedPassword).getBytes(StandardCharsets.UTF_8));
  }

  public static String getAccountAuthHeader(String bearerToken) {
    return "Bearer " + bearerToken;
  }

  public static String getUnidentifiedAccessHeader(byte[] key) {
    return Base64.encodeBytes(key);
  }
}
