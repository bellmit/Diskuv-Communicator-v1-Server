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
import org.whispersystems.textsecuregcm.storage.PendingDevices;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PendingDevicesTest {
  private static final UUID uuidAlice = UUID.fromString("25dec2e4-81a4-49c1-a182-a07fa9faf30f");
  private static final UUID uuidBob = UUID.fromString("a623c041-fd05-4b0a-a34a-761bf2f88f36");
  private static final UUID uuidMissing = UUID.fromString("85b47879-2c64-42e9-9a7b-960a6393b9f1");

  @Rule
  public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(LiquibasePreparer.forClasspathLocation("accountsdb.xml"));

  private PendingDevices pendingDevices;

  @Before
  public void setupAccountsDao() {
    this.pendingDevices = new PendingDevices(new FaultTolerantDatabase("peding_devices-test", Jdbi.create(db.getTestDatabase()), new CircuitBreakerConfiguration()));
  }

  @Test
  public void testStore() throws SQLException {
    pendingDevices.insert(uuidAlice, "1234", 1111);

    PreparedStatement statement = db.getTestDatabase().getConnection().prepareStatement("SELECT * FROM pending_devices WHERE number = ?");
    statement.setString(1, uuidAlice.toString());

    ResultSet resultSet = statement.executeQuery();

    if (resultSet.next()) {
      assertThat(resultSet.getString("verification_code")).isEqualTo("1234");
      assertThat(resultSet.getLong("timestamp")).isEqualTo(1111);
    } else {
      throw new AssertionError("no results");
    }

    assertThat(resultSet.next()).isFalse();
  }

  @Test
  public void testRetrieve() throws Exception {
    pendingDevices.insert(uuidAlice, "4321", 2222);
    pendingDevices.insert(uuidBob, "1212", 5555);

    Optional<StoredVerificationCode> verificationCode = pendingDevices.getCodeForPendingDevice(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);

    Optional<StoredVerificationCode> missingCode = pendingDevices.getCodeForPendingDevice(uuidMissing);
    assertThat(missingCode.isPresent()).isFalse();
  }

  @Test
  public void testOverwrite() throws Exception {
    pendingDevices.insert(uuidAlice, "4321", 2222);
    pendingDevices.insert(uuidAlice, "4444", 3333);

    Optional<StoredVerificationCode> verificationCode = pendingDevices.getCodeForPendingDevice(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4444");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(3333);
  }

  @Test
  public void testRemove() {
    pendingDevices.insert(uuidAlice, "4321", 2222);
    pendingDevices.insert(uuidBob, "1212", 5555);

    Optional<StoredVerificationCode> verificationCode = pendingDevices.getCodeForPendingDevice(uuidAlice);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);

    pendingDevices.remove(uuidAlice);

    verificationCode = pendingDevices.getCodeForPendingDevice(uuidAlice);
    assertThat(verificationCode.isPresent()).isFalse();

    verificationCode = pendingDevices.getCodeForPendingDevice(uuidBob);
    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("1212");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(5555);
  }

}
