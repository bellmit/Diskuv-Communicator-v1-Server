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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.*;

public class PendingDevicesTest {
  @Rule
  public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(LiquibasePreparer.forClasspathLocation("accountsdb.xml"));

  private PendingDevices pendingDevices;

  @Before
  public void setupAccountsDao() {
    this.pendingDevices = new PendingDevices(new FaultTolerantDatabase("peding_devices-test", Jdbi.create(db.getTestDatabase()), new CircuitBreakerConfiguration()));
  }

  @Test
  public void testStore() throws SQLException {
    pendingDevices.insert(UUID_ALICE, "1234", 1111);

    PreparedStatement statement = db.getTestDatabase().getConnection().prepareStatement("SELECT * FROM pending_devices WHERE number = ?");
    statement.setString(1, UUID_ALICE_STRING);

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
    pendingDevices.insert(UUID_ALICE, "4321", 2222);
    pendingDevices.insert(UUID_BOB, "1212", 5555);

    Optional<StoredVerificationCode> verificationCode = pendingDevices.getCodeForPendingDevice(UUID_ALICE);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);

    Optional<StoredVerificationCode> missingCode = pendingDevices.getCodeForPendingDevice(UUID_MISSING);
    assertThat(missingCode.isPresent()).isFalse();
  }

  @Test
  public void testOverwrite() throws Exception {
    pendingDevices.insert(UUID_ALICE, "4321", 2222);
    pendingDevices.insert(UUID_ALICE, "4444", 3333);

    Optional<StoredVerificationCode> verificationCode = pendingDevices.getCodeForPendingDevice(UUID_ALICE);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4444");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(3333);
  }

  @Test
  public void testRemove() {
    pendingDevices.insert(UUID_ALICE, "4321", 2222);
    pendingDevices.insert(UUID_BOB, "1212", 5555);

    Optional<StoredVerificationCode> verificationCode = pendingDevices.getCodeForPendingDevice(UUID_ALICE);

    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("4321");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(2222);

    pendingDevices.remove(UUID_ALICE);

    verificationCode = pendingDevices.getCodeForPendingDevice(UUID_ALICE);
    assertThat(verificationCode.isPresent()).isFalse();

    verificationCode = pendingDevices.getCodeForPendingDevice(UUID_BOB);
    assertThat(verificationCode.isPresent()).isTrue();
    assertThat(verificationCode.get().getCode()).isEqualTo("1212");
    assertThat(verificationCode.get().getTimestamp()).isEqualTo(5555);
  }

}
