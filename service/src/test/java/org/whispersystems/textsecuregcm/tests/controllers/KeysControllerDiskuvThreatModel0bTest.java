package org.whispersystems.textsecuregcm.tests.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.opentable.db.postgres.embedded.LiquibasePreparer;
import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.controllers.KeysController;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.entities.PreKeyCount;
import org.whispersystems.textsecuregcm.entities.PreKeyResponse;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.KeysDynamoDb;
import org.whispersystems.textsecuregcm.synthetic.HmacDrbg;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager;
import org.whispersystems.textsecuregcm.tests.util.UuidHelpers;

/**
 * Guard against threat TM0.b. Probing the API, whether through the Android/iOS client or directly,
 * should not reveal whether an account exists.
 */
public class KeysControllerDiskuvThreatModel0bTest {
  private static final Account ALICE_ACCOUNT = createAliceAccount();

  @Rule
  public PreparedDbRule accountsDb =
      EmbeddedPostgresRules.preparedDatabase(
          LiquibasePreparer.forClasspathLocation("accountsdb.xml"));

  private KeysController keysController;

  @Before
  public void setUp() throws RateLimitExceededException {
    // mock rate limiter
    RateLimiter rateLimiter = mock(RateLimiter.class);
    doNothing().when(rateLimiter).validate(anyString());
    doNothing().when(rateLimiter).validate(anyString(), anyInt());

    RateLimiters rateLimiters = mock(RateLimiters.class);
    when(rateLimiters.getPreKeysLimiter()).thenReturn(rateLimiter);

    // mock the accounts
    AccountsManager accounts = mock(AccountsManager.class);
    byte[] saltSharedSecret = new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES];

    PossiblySyntheticAccountsManager syntheticAccountsManager = new PossiblySyntheticAccountsManager(accounts, saltSharedSecret);

    KeysDynamoDb                keysDynamoDb                = mock(KeysDynamoDb.class);
    this.keysController = new KeysController(rateLimiters, keysDynamoDb, syntheticAccountsManager);
  }

  @Test
  public void givenNonExistentAccount__whenGetStatus__thenValidResponse() {
    PreKeyCount preKeyCount = keysController.getStatus(ALICE_ACCOUNT);
    assertThat(preKeyCount.getCount()).isEqualTo(0);
  }

  /**
   * The getDeviceKeys method is the method we have to care about. From one
   * authenticated account it lets you get the device keys for a different
   * account, as long as you the authenticated account has their unidentified
   * access key (or the other account has unrestricted access for unidentified
   * access). Regardless, do not want to raise a not found error.
   *
   * @throws RateLimitExceededException when too many get requests happen
   */
  @Test
  public void givenNonExistentAccount__whenGetDeviceKeys__thenValidResponse()
      throws RateLimitExceededException {
    Optional<PreKeyResponse> keys =
        keysController.getDeviceKeys(
            ALICE_ACCOUNT,
            Optional.empty(),
            new AmbiguousIdentifier(UuidHelpers.UUID_ALICE_STRING),
            Long.toString(Device.MASTER_ID));
    assertThat(keys).isPresent();
    assertThat(keys.get().getDevicesCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  public void givenNonExistentAccount__whenGetSignedKey__thenValidResponse() {
    Optional<SignedPreKey> signedKey = keysController.getSignedKey(ALICE_ACCOUNT);
    assertThat(signedKey).isPresent();
    assertThat(signedKey.get().getKeyId()).isEqualTo(0);
  }

  private static Account createAliceAccount() {
    Device device = new Device();
    device.setSignedPreKey(new SignedPreKey());

    Account account =
        new Account(UuidHelpers.UUID_ALICE, ImmutableSet.of(), new byte[0]);
    account.setAuthenticatedDevice(device);
    return account;
  }
}
