package org.whispersystems.textsecuregcm.tests.storage;

import com.opentable.db.postgres.embedded.LiquibasePreparer;
import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;
import org.jdbi.v3.core.Jdbi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.textsecuregcm.auth.StoredVerificationCode;
import org.whispersystems.textsecuregcm.configuration.CircuitBreakerConfiguration;
import org.whispersystems.textsecuregcm.storage.FaultTolerantDatabase;
import org.whispersystems.textsecuregcm.storage.PendingAccounts;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PendingAccountsTest {
  private static final UUID uuidAlice = UUID.fromString("25dec2e4-81a4-49c1-a182-a07fa9faf30f");
  private static final UUID uuidBob = UUID.fromString("a623c041-fd05-4b0a-a34a-761bf2f88f36");
  private static final UUID uuidMissing = UUID.fromString("85b47879-2c64-42e9-9a7b-960a6393b9f1");

  @Rule
  public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(LiquibasePreparer.forClasspathLocation("accountsdb.xml"));

  private PendingAccounts pendingAccounts;

  @Before
  public void setupAccountsDao() {
    this.pendingAccounts = new PendingAccounts(new FaultTolerantDatabase("pending_accounts-test", Jdbi.create(db.getTestDatabase()), new CircuitBreakerConfiguration()));
  }

  @Test
  public void testStore() throws SQLException {
    pendingAccounts.insert(uuidAlice, "1234", 1111, null);

    PreparedStatement statement = db.getTestDatabase().getConnection().prepareStatement("SELECT * FROM pending_accounts WHERE number = ?");
    statement.setString(1, uuidAlice.toString());

    ResultSet resultSet = statement.executeQuery();

    if (resultSet.next()) {
      assertThat(resultSet.getString("verification_code")).isEqualTo("1234");
      assertThat(resultSet.getLong("timestamp")).isEqualTo(1111);
      assertThat(resultSet.getString("push_code")).isNull();
    } else {
      throw new AssertionError("no results");
    }

    assertThat(resultSet.next()).isFalse();
  }

  @Test
  public void testStoreWithPushChallenge() throws SQLException {
    pendingAccounts.insert(uuidAlice, null, 1111,  "112233");

    PreparedStatement statement = db.getTestDatabase().getConnection().prepareStatement("SELECT * FROM pending_accounts WHERE number = ?");
    statement.setString(1, "+14151112222");

    ResultSet resultSet = statement.executeQuery();

    if (resultSet.next()) {
      assertThat(resultSet.getString("verification_code")).isNull();
      assertThat(resultSet.getLong("timestamp")).isEqualTo(1111);
      assertThat(resultSet.getString("push_code")).isEqualTo("112233");
    } else {
      throw new AssertionError("no results");
    }

    assertThat(resultSet.next()).isFalse();
  }

  @Test
  public void testRetrieve() {
    pendingAccounts.insert(uuidAlice, "4321", 2222, null);
    pendingAccounts.insert(uuidBob, "1212", 5555, null);

    Optional<StoredVerificationCode> verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);

    Optional<StoredVerificationCode> missingCode = pendingAccounts.getCodeForPendingAccount(uuidMissing);
    assertThat(missingCode.isPresent()).isFalse();
  }

  @Test
  public void testRetrieveWithPushChallenge() {
    pendingAccounts.insert(uuidAlice, "4321", 2222, "bar");
    pendingAccounts.insert(uuidBob, "1212", 5555, "bang");

    Optional<StoredVerificationCode> verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);
    assertThat(verificationCode.get().getPushCode()).isEqualTo("bar");

    Optional<StoredVerificationCode> missingCode = pendingAccounts.getCodeForPendingAccount(uuidMissing);
    assertThat(missingCode.isPresent()).isFalse();
  }

  @Test
  public void testOverwrite() {
    pendingAccounts.insert(uuidAlice, "4321", 2222, null);
    pendingAccounts.insert(uuidAlice, "4444", 3333, null);

    Optional<StoredVerificationCode> verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4444");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(3333);
  }

  @Test
  public void testOverwriteWithPushToken() {
    pendingAccounts.insert(uuidAlice, "4321", 2222, "bar");
    pendingAccounts.insert(uuidAlice, "4444", 3333, "bang");

    Optional<StoredVerificationCode> verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4444");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(3333);
    assertThat(verificationCode.get().getPushCode()).isEqualTo("bang");
  }


  @Test
  public void testVacuum() {
    pendingAccounts.insert(uuidAlice, "4321", 2222, null);
    pendingAccounts.insert(uuidAlice, "4444", 3333, null);
    pendingAccounts.vacuum();

    Optional<StoredVerificationCode> verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4444");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(3333);
  }

  @Test
  public void testRemove() {
    pendingAccounts.insert(uuidAlice, "4321", 2222, "bar");
    pendingAccounts.insert(uuidBob, "1212", 5555, null);

    Optional<StoredVerificationCode> verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);

    pendingAccounts.remove(uuidAlice);

    verificationCode = pendingAccounts.getCodeForPendingAccount(uuidAlice);
    assertThat(verificationCode.isPresent()).isFalse();

    verificationCode = pendingAccounts.getCodeForPendingAccount(uuidBob);
    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("1212");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(5555);
    assertThat(verificationCode.get().getPushCode()).isNull();
  }


}
