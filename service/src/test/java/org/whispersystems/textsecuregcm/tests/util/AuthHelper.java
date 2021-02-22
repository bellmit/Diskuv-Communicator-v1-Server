package org.whispersystems.textsecuregcm.tests.util;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.PolymorphicAuthDynamicFeature;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import java.security.Principal;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
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

public class AuthHelper {
  public static final String VALID_BEARER_TOKEN = "blah";
  public static final String VALID_EMAIL        = "email@test.com";
  public static final long   VALID_DEVICE_ID    = 1L;
  public static final String VALID_DEVICE_ID_STRING = Long.toString(VALID_DEVICE_ID);
  public static final String VALID_NUMBER       = "+14150000000";
  public static final UUID   VALID_UUID         = org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.uuidForOutdoorEmailAddress(VALID_EMAIL);
  public static final String VALID_PASSWORD     = "foo";

  public static final String VALID_BEARER_TOKEN_TWO     = "bliss";
  public static final String VALID_EMAIL_TWO            = "email2@test.com";
  public static final long   VALID_DEVICE_ID_TWO        = 1L;
  public static final String VALID_DEVICE_ID_STRING_TWO = Long.toString(VALID_DEVICE_ID_TWO);
  public static final String VALID_NUMBER_TWO = "+201511111110";
  public static final UUID   VALID_UUID_TWO    = org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.uuidForOutdoorEmailAddress(VALID_EMAIL_TWO);
  public static final String VALID_PASSWORD_TWO = "baz";

  // Static seed to ensure reproducible tests.
  private static final Random random = new Random(0xf744df3b43a3339cL);

  public static final TestAccount[] TEST_ACCOUNTS = generateTestAccounts();

  public static final String INVALID_BEARER_TOKEN = "bleak";
  public static final long   INVALID_DEVICE_ID    = 1L;
  public static final String INVALID_DEVICE_ID_STRING = Long.toString(INVALID_DEVICE_ID);
  public static final String INVVALID_NUMBER  = "+14151111111";
  public static final UUID   INVALID_UUID     = UUID.randomUUID();
  public static final String INVALID_PASSWORD = "bar";

  public static final String DISABLED_BEARER_TOKEN     = "bat";
  public static final long   DISABLED_DEVICE_ID        = 1L;
  public static final String DISABLED_DEVICE_ID_STRING = Long.toString(DISABLED_DEVICE_ID);
  public static final String DISABLED_EMAIL            = "disbled@test.com";
  public static final String DISABLED_NUMBER           = "+78888888";
  public static final UUID   DISABLED_UUID             = org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.uuidForOutdoorEmailAddress(DISABLED_EMAIL);
  public static final String DISABLED_PASSWORD         = "poof";

  public static final String UNDISCOVERABLE_BEARER_TOKEN     = "blinky";
  public static final long   UNDISCOVERABLE_DEVICE_ID = 1L;
  public static final String UNDISCOVERABLE_EMAIL            = "undiscoverable@test.com";
  public static final String UNDISCOVERABLE_NUMBER   = "+18005551234";
  public static final UUID   UNDISCOVERABLE_UUID     = UUID.randomUUID();
  public static final String UNDISCOVERABLE_PASSWORD = "IT'S A SECRET TO EVERYBODY.";

  public static final String VALID_IDENTITY = "BcxxDU9FGMda70E7+Uvm7pnQcEdXQ64aJCpPUeRSfcFo";
  public static JwtAuthentication JWT_AUTHENTICATION = mock(JwtAuthentication.class);

  public static AccountsManager ACCOUNTS_MANAGER       = mock(AccountsManager.class);
  public static Account         VALID_ACCOUNT          = mock(Account.class        );
  public static Account         VALID_ACCOUNT_TWO      = mock(Account.class        );
  public static Account         DISABLED_ACCOUNT       = mock(Account.class        );
  public static Account         UNDISCOVERABLE_ACCOUNT = mock(Account.class        );

  public static Device VALID_DEVICE          = mock(Device.class);
  public static Device VALID_DEVICE_TWO      = mock(Device.class);
  public static Device DISABLED_DEVICE       = mock(Device.class);
  public static Device UNDISCOVERABLE_DEVICE = mock(Device.class);

  private static AuthenticationCredentials VALID_CREDENTIALS          = mock(AuthenticationCredentials.class);
  private static AuthenticationCredentials VALID_CREDENTIALS_TWO      = mock(AuthenticationCredentials.class);
  private static AuthenticationCredentials DISABLED_CREDENTIALS       = mock(AuthenticationCredentials.class);
  private static AuthenticationCredentials UNDISCOVERABLE_CREDENTIALS = mock(AuthenticationCredentials.class);

  public static PolymorphicAuthDynamicFeature<? extends Principal> getAuthFilter() {
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(VALID_BEARER_TOKEN)).thenReturn(VALID_EMAIL);
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(VALID_BEARER_TOKEN_TWO)).thenReturn(VALID_EMAIL_TWO);
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(DISABLED_BEARER_TOKEN)).thenReturn(DISABLED_EMAIL);
    when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(UNDISCOVERABLE_BEARER_TOKEN)).thenReturn(UNDISCOVERABLE_EMAIL);

    when(VALID_CREDENTIALS.verify(VALID_PASSWORD)).thenReturn(true);
    when(VALID_CREDENTIALS_TWO.verify(VALID_PASSWORD_TWO)).thenReturn(true);
    when(DISABLED_CREDENTIALS.verify(DISABLED_PASSWORD)).thenReturn(true);
    when(UNDISCOVERABLE_CREDENTIALS.verify(UNDISCOVERABLE_PASSWORD)).thenReturn(true);

    when(VALID_CREDENTIALS.verify(VALID_PASSWORD.getBytes(java.nio.charset.StandardCharsets.UTF_8))).thenReturn(true);
    when(VALID_CREDENTIALS_TWO.verify(VALID_PASSWORD_TWO.getBytes(java.nio.charset.StandardCharsets.UTF_8))).thenReturn(true);
    when(DISABLED_CREDENTIALS.verify(DISABLED_PASSWORD.getBytes(java.nio.charset.StandardCharsets.UTF_8))).thenReturn(true);
    when(UNDISCOVERABLE_CREDENTIALS.verify(UNDISCOVERABLE_PASSWORD.getBytes(java.nio.charset.StandardCharsets.UTF_8))).thenReturn(true);

    when(VALID_DEVICE.getAuthenticationCredentials()).thenReturn(VALID_CREDENTIALS);
    when(VALID_DEVICE_TWO.getAuthenticationCredentials()).thenReturn(VALID_CREDENTIALS_TWO);
    when(DISABLED_DEVICE.getAuthenticationCredentials()).thenReturn(DISABLED_CREDENTIALS);
    when(UNDISCOVERABLE_DEVICE.getAuthenticationCredentials()).thenReturn(UNDISCOVERABLE_CREDENTIALS);

    when(VALID_DEVICE.isMaster()).thenReturn(true);
    when(VALID_DEVICE_TWO.isMaster()).thenReturn(true);
    when(DISABLED_DEVICE.isMaster()).thenReturn(true);
    when(UNDISCOVERABLE_DEVICE.isMaster()).thenReturn(true);

    when(VALID_DEVICE.getId()).thenReturn(VALID_DEVICE_ID);
    when(VALID_DEVICE_TWO.getId()).thenReturn(VALID_DEVICE_ID_TWO);
    when(DISABLED_DEVICE.getId()).thenReturn(DISABLED_DEVICE_ID);
    when(UNDISCOVERABLE_DEVICE.getId()).thenReturn(UNDISCOVERABLE_DEVICE_ID);

    when(VALID_DEVICE.isEnabled()).thenReturn(true);
    when(VALID_DEVICE_TWO.isEnabled()).thenReturn(true);
    when(DISABLED_DEVICE.isEnabled()).thenReturn(false);
    when(UNDISCOVERABLE_DEVICE.isMaster()).thenReturn(true);

    when(VALID_ACCOUNT.getDevice(VALID_DEVICE_ID)).thenReturn(Optional.of(VALID_DEVICE));
    when(VALID_ACCOUNT.getMasterDevice()).thenReturn(Optional.of(VALID_DEVICE));
    when(VALID_ACCOUNT_TWO.getDevice(VALID_DEVICE_ID_TWO)).thenReturn(Optional.of(VALID_DEVICE_TWO));
    when(VALID_ACCOUNT_TWO.getMasterDevice()).thenReturn(Optional.of(VALID_DEVICE_TWO));
    when(DISABLED_ACCOUNT.getDevice(DISABLED_DEVICE_ID)).thenReturn(Optional.of(DISABLED_DEVICE));
    when(DISABLED_ACCOUNT.getMasterDevice()).thenReturn(Optional.of(DISABLED_DEVICE));
    when(UNDISCOVERABLE_ACCOUNT.getDevice(UNDISCOVERABLE_DEVICE_ID)).thenReturn(Optional.of(UNDISCOVERABLE_DEVICE));
    when(UNDISCOVERABLE_ACCOUNT.getMasterDevice()).thenReturn(Optional.of(UNDISCOVERABLE_DEVICE));

    when(VALID_ACCOUNT_TWO.getEnabledDeviceCount()).thenReturn(6);

    when(VALID_ACCOUNT.getNumber()).thenReturn(VALID_NUMBER);
    when(VALID_ACCOUNT.getUuid()).thenReturn(VALID_UUID);
    when(VALID_ACCOUNT_TWO.getNumber()).thenReturn(VALID_NUMBER_TWO);
    when(VALID_ACCOUNT_TWO.getUuid()).thenReturn(VALID_UUID_TWO);
    when(DISABLED_ACCOUNT.getNumber()).thenReturn(DISABLED_NUMBER);
    when(DISABLED_ACCOUNT.getUuid()).thenReturn(DISABLED_UUID);
    when(UNDISCOVERABLE_ACCOUNT.getNumber()).thenReturn(UNDISCOVERABLE_NUMBER);
    when(UNDISCOVERABLE_ACCOUNT.getUuid()).thenReturn(UNDISCOVERABLE_UUID);

    when(VALID_ACCOUNT.getAuthenticatedDevice()).thenReturn(Optional.of(VALID_DEVICE));
    when(VALID_ACCOUNT_TWO.getAuthenticatedDevice()).thenReturn(Optional.of(VALID_DEVICE_TWO));
    when(DISABLED_ACCOUNT.getAuthenticatedDevice()).thenReturn(Optional.of(DISABLED_DEVICE));
    when(UNDISCOVERABLE_ACCOUNT.getAuthenticatedDevice()).thenReturn(Optional.of(UNDISCOVERABLE_DEVICE));

    when(VALID_ACCOUNT.getRelay()).thenReturn(Optional.empty());
    when(VALID_ACCOUNT_TWO.getRelay()).thenReturn(Optional.empty());
    when(UNDISCOVERABLE_ACCOUNT.getRelay()).thenReturn(Optional.empty());

    when(VALID_ACCOUNT.isEnabled()).thenReturn(true);
    when(VALID_ACCOUNT_TWO.isEnabled()).thenReturn(true);
    when(DISABLED_ACCOUNT.isEnabled()).thenReturn(false);
    when(UNDISCOVERABLE_ACCOUNT.isEnabled()).thenReturn(true);

    when(VALID_ACCOUNT.isDiscoverableByPhoneNumber()).thenReturn(true);
    when(VALID_ACCOUNT_TWO.isDiscoverableByPhoneNumber()).thenReturn(true);
    when(DISABLED_ACCOUNT.isDiscoverableByPhoneNumber()).thenReturn(true);
    when(UNDISCOVERABLE_ACCOUNT.isDiscoverableByPhoneNumber()).thenReturn(false);

    when(VALID_ACCOUNT.getRealAccount()).thenReturn(Optional.of(VALID_ACCOUNT));
    when(VALID_ACCOUNT_TWO.getRealAccount()).thenReturn(Optional.of(VALID_ACCOUNT_TWO));
    when(DISABLED_ACCOUNT.getRealAccount()).thenReturn(Optional.of(DISABLED_ACCOUNT));

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

    when(ACCOUNTS_MANAGER.get(UNDISCOVERABLE_NUMBER)).thenReturn(Optional.of(UNDISCOVERABLE_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(UNDISCOVERABLE_UUID)).thenReturn(Optional.of(UNDISCOVERABLE_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(UNDISCOVERABLE_NUMBER)))).thenReturn(Optional.of(UNDISCOVERABLE_ACCOUNT));
    when(ACCOUNTS_MANAGER.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(UNDISCOVERABLE_UUID)))).thenReturn(Optional.of(UNDISCOVERABLE_ACCOUNT));

    for (TestAccount testAccount : TEST_ACCOUNTS) {
      testAccount.setup(ACCOUNTS_MANAGER);
    }

    AuthFilter<DiskuvDeviceCredentials, Account>                  accountAuthFilter                  = new DiskuvDeviceCredentialAuthFilter.Builder<Account>().setAuthenticator(new DiskuvAccountAuthenticator(ACCOUNTS_MANAGER, JWT_AUTHENTICATION)).buildAuthFilter();
    AuthFilter<DiskuvDeviceCredentials, DisabledPermittedAccount> disabledPermittedAccountAuthFilter = new DiskuvDeviceCredentialAuthFilter.Builder<DisabledPermittedAccount>().setAuthenticator(new DisabledPermittedDiskuvAccountAuthenticator(ACCOUNTS_MANAGER, JWT_AUTHENTICATION)).buildAuthFilter();

    return new PolymorphicAuthDynamicFeature<>(ImmutableMap.of(Account.class, accountAuthFilter,
                                                               DisabledPermittedAccount.class, disabledPermittedAccountAuthFilter));
  }

  public static String getAuthHeader(String number, String password) {
    return getAuthHeader(VALID_UUID, number, password);
  }

  public static String getAuthHeader(UUID accountUuid, String number, String password) {
    String encodedPassword = Base64.encodeBytes(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return "Basic " + Base64.encodeBytes((accountUuid + ":" + number + ":" + encodedPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public static String getAccountAuthHeader(String bearerToken) {
    return "Bearer " + bearerToken;
  }

  public static String getUnidentifiedAccessHeader(byte[] key) {
    return Base64.encodeBytes(key);
  }

  public static UUID getRandomUUID(Random random) {
    long mostSignificantBits  = random.nextLong();
    long leastSignificantBits = random.nextLong();
    mostSignificantBits  &= 0xffffffffffff0fffL;
    mostSignificantBits  |= 0x0000000000004000L;
    leastSignificantBits &= 0x3fffffffffffffffL;
    leastSignificantBits |= 0x8000000000000000L;
    return new UUID(mostSignificantBits, leastSignificantBits);
  }

  public static final class TestAccount {
    public final String email;
    public final UUID   uuid;
    public final String                    password;
    public final Account                   account                   = mock(Account.class);
    public final Device                    device                    = mock(Device.class);
    public final AuthenticationCredentials authenticationCredentials = mock(AuthenticationCredentials.class);
    public final String                    bearerToken;

    public TestAccount(String email, UUID uuid, String password) {
      this.email = email;
      this.uuid  = uuid;
      this.password = password;
      this.bearerToken = Hashing.sha256().hashString(email, java.nio.charset.StandardCharsets.UTF_8).toString();
    }

    public String getAuthHeader() {
      return AuthHelper.getAccountAuthHeader(bearerToken);
    }

    private void setup(final AccountsManager accountsManager) {
      when(authenticationCredentials.verify(password)).thenReturn(true);
      when(authenticationCredentials.verify(password.getBytes(java.nio.charset.StandardCharsets.UTF_8))).thenReturn(true);
      when(device.getAuthenticationCredentials()).thenReturn(authenticationCredentials);
      when(device.isMaster()).thenReturn(true);
      when(device.getId()).thenReturn(1L);
      when(device.isEnabled()).thenReturn(true);
      when(account.getDevice(1L)).thenReturn(Optional.of(device));
      when(account.getMasterDevice()).thenReturn(Optional.of(device));
      when(account.getNumber()).thenReturn(uuid.toString());
      when(account.getUuid()).thenReturn(uuid);
      when(account.getAuthenticatedDevice()).thenReturn(Optional.of(device));
      when(account.getRelay()).thenReturn(Optional.empty());
      when(account.isEnabled()).thenReturn(true);
      when(JWT_AUTHENTICATION.verifyBearerTokenAndGetEmailAddress(bearerToken)).thenReturn(email);
      when(accountsManager.get(email)).thenReturn(Optional.of(account));
      when(accountsManager.get(uuid)).thenReturn(Optional.of(account));
      when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(email)))).thenReturn(Optional.of(account));
      when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(uuid)))).thenReturn(Optional.of(account));
    }
  }

  private static TestAccount[] generateTestAccounts() {
    final TestAccount[] testAccounts = new TestAccount[20];
    final long numberBase = 1_409_000_0000L;
    for (int i = 0; i < testAccounts.length; i++) {
      long currentNumber = numberBase + i;
      String email       = currentNumber + "@example.com";
      UUID uuid          = org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.uuidForOutdoorEmailAddress(email);
      testAccounts[i]    = new TestAccount(email, uuid, "TestAccountPassword-" + currentNumber);
    }
    return testAccounts;
  }
}
