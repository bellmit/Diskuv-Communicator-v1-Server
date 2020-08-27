 package org.whispersystems.textsecuregcm.tests.storage;

import com.google.protobuf.ByteString;
import com.opentable.db.postgres.embedded.LiquibasePreparer;
import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;
import org.jdbi.v3.core.Jdbi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.textsecuregcm.configuration.CircuitBreakerConfiguration;
import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntity;
import org.whispersystems.textsecuregcm.storage.FaultTolerantDatabase;
import org.whispersystems.textsecuregcm.storage.Messages;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.*;

public class MessagesTest {

  @Rule
  public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(LiquibasePreparer.forClasspathLocation("messagedb.xml"));

  private Messages messages;
  private Set<Integer> timestampSampleWithoutReplacements;

  private long serialTimestamp = 0;

  @Before
  public void setupAccountsDao() {
    this.messages = new Messages(new FaultTolerantDatabase("messages-test", Jdbi.create(db.getTestDatabase()), new CircuitBreakerConfiguration()));
    this.timestampSampleWithoutReplacements = ConcurrentHashMap.newKeySet();
  }

  @Test
  public void testStore() throws SQLException {
    Envelope envelope = generateEnvelope();
    UUID     guid     = UUID.randomUUID();

    messages.store(guid, envelope, UUID_ALICE_STRING, 1);

    PreparedStatement statement = db.getTestDatabase().getConnection().prepareStatement("SELECT * FROM messages WHERE destination = ?");
    statement.setString(1, UUID_ALICE_STRING);

    ResultSet resultSet = statement.executeQuery();
    assertThat(resultSet.next()).isTrue();

    assertThat(resultSet.getString("guid")).isEqualTo(guid.toString());
    assertThat(resultSet.getInt("type")).isEqualTo(envelope.getType().getNumber());
    assertThat(resultSet.getString("relay")).isNullOrEmpty();
    assertThat(resultSet.getLong("timestamp")).isEqualTo(envelope.getTimestamp());
    assertThat(resultSet.getLong("server_timestamp")).isEqualTo(envelope.getServerTimestamp());
    assertThat(resultSet.getString("source_uuid")).isEqualTo(envelope.getSourceUuid());
    assertThat(resultSet.getLong("source_device")).isEqualTo(envelope.getSourceDevice());
    assertThat(resultSet.getBytes("message")).isEqualTo(envelope.getLegacyMessage().toByteArray());
    assertThat(resultSet.getBytes("content")).isEqualTo(envelope.getContent().toByteArray());
    assertThat(resultSet.getString("destination")).isEqualTo(UUID_ALICE_STRING);
    assertThat(resultSet.getLong("destination_device")).isEqualTo(1);

    assertThat(resultSet.next()).isFalse();
  }

  @Test
  public void testLoad() {
    List<MessageToStore> inserted = new ArrayList<>(50);

    for (int i=0;i<50;i++) {
      MessageToStore message = generateMessageToStore();
      inserted.add(message);

      messages.store(message.guid, message.envelope, UUID_ALICE_STRING, 1);
    }

    inserted.sort(Comparator.comparingLong(o -> o.envelope.getTimestamp()));

    List<OutgoingMessageEntity> retrieved = messages.load(UUID_ALICE_STRING, 1);

    assertThat(retrieved.size()).isEqualTo(inserted.size());

    for (int i=0;i<retrieved.size();i++) {
      verifyExpected(retrieved.get(i), inserted.get(i).envelope, inserted.get(i).guid);
    }

  }

  @Test
  public void removeBySourceDestinationTimestamp() {
    List<MessageToStore>            inserted = insertRandom(UUID_ALICE, 1);
    List<MessageToStore>            unrelated = insertRandom(UUID_BOB, 3);
    MessageToStore                  toRemove = inserted.remove(new Random(System.currentTimeMillis()).nextInt(inserted.size() - 1));
    Optional<OutgoingMessageEntity> removed  = messages.remove(UUID_ALICE_STRING, 1, toRemove.envelope.getSourceUuid(), toRemove.envelope.getTimestamp());

    assertThat(removed.isPresent()).isTrue();
    verifyExpected(removed.get(), toRemove.envelope, toRemove.guid);

    verifyInTact(inserted, UUID_ALICE, 1);
    verifyInTact(unrelated, UUID_BOB, 3);
  }

  @Test
  public void removeByDestinationGuid() {
    List<MessageToStore>            unrelated = insertRandom(UUID_BOB, 2);
    List<MessageToStore>            inserted = insertRandom(UUID_ALICE, 1);
    MessageToStore                  toRemove = inserted.remove(new Random(System.currentTimeMillis()).nextInt(inserted.size() - 1));
    Optional<OutgoingMessageEntity> removed  = messages.remove(UUID_ALICE_STRING, toRemove.guid);

    assertThat(removed.isPresent()).isTrue();
    verifyExpected(removed.get(), toRemove.envelope, toRemove.guid);

    verifyInTact(inserted, UUID_ALICE, 1);
    verifyInTact(unrelated, UUID_BOB, 2);
  }

  @Test
  public void removeByDestinationRowId() {
    List<MessageToStore> unrelatedInserted = insertRandom(UUID_BOB, 1);
    List<MessageToStore> inserted          = insertRandom(UUID_ALICE, 1);

    inserted.sort(Comparator.comparingLong(o -> o.envelope.getTimestamp()));

    List<OutgoingMessageEntity> retrieved = messages.load(UUID_ALICE_STRING, 1);

    int toRemoveIndex = new Random(System.currentTimeMillis()).nextInt(inserted.size() - 1);

    inserted.remove(toRemoveIndex);

    messages.remove(UUID_ALICE_STRING, retrieved.get(toRemoveIndex).getId());

    verifyInTact(inserted, UUID_ALICE, 1);
    verifyInTact(unrelatedInserted, UUID_BOB, 1);
  }

  @Test
  public void testLoadEmpty() {
    List<MessageToStore> inserted = insertRandom(UUID_ALICE, 1);
    List<OutgoingMessageEntity> loaded = messages.load(UUID_MISSING_STRING, 1);
    assertThat(loaded.isEmpty()).isTrue();
  }

  @Test
  public void testClearDestination() {
    insertRandom(UUID_ALICE, 1);
    insertRandom(UUID_ALICE, 2);

    List<MessageToStore> unrelated = insertRandom(UUID_BOB, 1);

    messages.clear(UUID_ALICE_STRING);

    assertThat(messages.load(UUID_ALICE_STRING, 1).isEmpty()).isTrue();

    verifyInTact(unrelated, UUID_BOB, 1);
  }

  @Test
  public void testClearDestinationDevice() {
    insertRandom(UUID_ALICE, 1);
    List<MessageToStore> inserted = insertRandom(UUID_ALICE, 2);

    List<MessageToStore> unrelated = insertRandom(UUID_BOB, 1);

    messages.clear(UUID_ALICE_STRING, 1);

    assertThat(messages.load(UUID_ALICE_STRING, 1).isEmpty()).isTrue();

    verifyInTact(inserted, UUID_ALICE, 2);
    verifyInTact(unrelated, UUID_BOB, 1);
  }

  @Test
  public void testVacuum() {
    List<MessageToStore> inserted = insertRandom(UUID_ALICE, 2);
    messages.vacuum();
    verifyInTact(inserted, UUID_ALICE, 2);
  }

  private List<MessageToStore> insertRandom(UUID destination, int destinationDevice) {
    List<MessageToStore> inserted = new ArrayList<>(50);

    for (int i=0;i<50;i++) {
      MessageToStore message = generateMessageToStore();
      inserted.add(message);

      messages.store(message.guid, message.envelope, destination.toString(), destinationDevice);
    }

    return inserted;
  }

  private void verifyInTact(List<MessageToStore> inserted, UUID destination, int destinationDevice) {
    inserted.sort(Comparator.comparingLong(o -> o.envelope.getTimestamp()));

    List<OutgoingMessageEntity> retrieved = messages.load(destination.toString(), destinationDevice);

    assertThat(retrieved.size()).isEqualTo(inserted.size());

    for (int i=0;i<retrieved.size();i++) {
      verifyExpected(retrieved.get(i), inserted.get(i).envelope, inserted.get(i).guid);
    }
  }


  private void verifyExpected(OutgoingMessageEntity retrieved, Envelope inserted, UUID guid) {
    assertThat(retrieved.getTimestamp()).isEqualTo(inserted.getTimestamp());
    assertThat(retrieved.getSource() == null ? "" : retrieved.getSource()).isEqualTo(inserted.getSource());
    assertThat(retrieved.getSourceUuid() == null ? "" : retrieved.getSourceUuid().toString()).isEqualTo(inserted.getSourceUuid());
    assertThat(retrieved.getRelay()).isEqualTo(inserted.getRelay());
    assertThat(retrieved.getType()).isEqualTo(inserted.getType().getNumber());
    assertThat(retrieved.getContent()).isEqualTo(inserted.getContent().toByteArray());
    assertThat(retrieved.getMessage()).isEqualTo(inserted.getLegacyMessage().toByteArray());
    assertThat(retrieved.getServerTimestamp()).isEqualTo(inserted.getServerTimestamp());
    assertThat(retrieved.getGuid()).isEqualTo(guid);
    assertThat(retrieved.getSourceDevice()).isEqualTo(inserted.getSourceDevice());
  }

  private MessageToStore generateMessageToStore() {
    return new MessageToStore(UUID.randomUUID(), generateEnvelope());
  }

  private Envelope generateEnvelope() {
    Random random = new Random();
    byte[] content = new byte[256];
    byte[] legacy = new byte[200];

    Arrays.fill(content, (byte)random.nextInt(255));
    Arrays.fill(legacy, (byte)random.nextInt(255));

    // verifyInTact will fail if we generate two same timestamps
    Integer timestamp = null;
    for (int i=0; i<100; ++i) {
      int ts = random.nextInt(100000);
      if (this.timestampSampleWithoutReplacements.add(ts)) {
        // successfully added. that is a new, unique timestamp
        timestamp = ts;
      }
    }
    assertThat(timestamp).isNotNull();

    return Envelope.newBuilder()
                   .setSourceDevice(random.nextInt(10000))
                   .setSourceUuid(org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.uuidForOutdoorEmailAddress("testSource" + random.nextInt() + "@example.com").toString())
                   .setTimestamp(serialTimestamp++)
                   .setServerTimestamp(serialTimestamp++)
                   .setLegacyMessage(ByteString.copyFrom(legacy))
                   .setContent(ByteString.copyFrom(content))
                   .setType(Envelope.Type.CIPHERTEXT)
                   .setServerGuid(UUID.randomUUID().toString())
                   .setServerOutdoorsSourceUuid(UUID.randomUUID().toString())
                   .build();
  }

  private static class MessageToStore {
    private final UUID guid;
    private final Envelope envelope;

    private MessageToStore(UUID guid, Envelope envelope) {
      this.guid = guid;
      this.envelope = envelope;
    }
  }
}
