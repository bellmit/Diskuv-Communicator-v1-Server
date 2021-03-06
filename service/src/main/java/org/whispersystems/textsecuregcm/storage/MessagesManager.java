package org.whispersystems.textsecuregcm.storage;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntity;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntityList;
import org.whispersystems.textsecuregcm.metrics.PushLatencyManager;
import org.whispersystems.textsecuregcm.redis.RedisOperation;
import org.whispersystems.textsecuregcm.util.Constants;

public class MessagesManager {

  private static final int RESULT_SET_CHUNK_SIZE = 100;

  private static final MetricRegistry metricRegistry       = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private static final Meter          cacheHitByNameMeter  = metricRegistry.meter(name(MessagesManager.class, "cacheHitByName" ));
  private static final Meter          cacheMissByNameMeter = metricRegistry.meter(name(MessagesManager.class, "cacheMissByName"));
  private static final Meter          cacheHitByGuidMeter  = metricRegistry.meter(name(MessagesManager.class, "cacheHitByGuid" ));
  private static final Meter          cacheMissByGuidMeter = metricRegistry.meter(name(MessagesManager.class, "cacheMissByGuid"));

  private final MessagesDynamoDb messagesDynamoDb;
  private final MessagesCache messagesCache;
  private final PushLatencyManager pushLatencyManager;

  public MessagesManager(
      MessagesDynamoDb messagesDynamoDb,
      MessagesCache messagesCache,
      PushLatencyManager pushLatencyManager) {
    this.messagesDynamoDb = messagesDynamoDb;
    this.messagesCache = messagesCache;
    this.pushLatencyManager = pushLatencyManager;
  }

  public void insert(UUID destinationUuid, long destinationDevice, Envelope message) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.insert(UUID.randomUUID(), destinationUuid, destinationDevice, message);
  }

  public void insertEphemeral(final UUID destinationUuid, final long destinationDevice, final Envelope message) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.insertEphemeral(destinationUuid, destinationDevice, message);
  }

  public Optional<Envelope> takeEphemeralMessage(final UUID destinationUuid, final long destinationDevice) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    return messagesCache.takeEphemeralMessage(destinationUuid, destinationDevice);
  }

  public boolean hasCachedMessages(final UUID destinationUuid, final long destinationDevice) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    return messagesCache.hasMessages(destinationUuid, destinationDevice);
  }

  public OutgoingMessageEntityList getMessagesForDevice(UUID destinationUuid, long destinationDevice, final String userAgent, final boolean cachedMessagesOnly) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    RedisOperation.unchecked(() -> pushLatencyManager.recordQueueRead(destinationUuid, destinationDevice, userAgent));

    List<OutgoingMessageEntity> messageList = new ArrayList<>();

    if (!cachedMessagesOnly) {
      messageList.addAll(messagesDynamoDb.load(destinationUuid, destinationDevice, RESULT_SET_CHUNK_SIZE));
    }

    if (messageList.size() < RESULT_SET_CHUNK_SIZE) {
      messageList.addAll(messagesCache.get(destinationUuid, destinationDevice, RESULT_SET_CHUNK_SIZE - messageList.size()));
    }

    return new OutgoingMessageEntityList(messageList, messageList.size() >= RESULT_SET_CHUNK_SIZE);
  }

  public void clear(UUID destinationUuid) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.clear(destinationUuid);
    messagesDynamoDb.deleteAllMessagesForAccount(destinationUuid);
  }

  public void clear(UUID destinationUuid, long deviceId) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.clear(destinationUuid, deviceId);
    messagesDynamoDb.deleteAllMessagesForDevice(destinationUuid, deviceId);
  }

  public Optional<OutgoingMessageEntity> delete(
      UUID destinationUuid, long destinationDeviceId, UUID sourceUuid, long timestamp) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    Optional<OutgoingMessageEntity> removed = messagesCache.remove(destinationUuid, destinationDeviceId, sourceUuid.toString(), timestamp);

    if (removed.isEmpty()) {
      removed = messagesDynamoDb.deleteMessageByDestinationAndSourceUuidAndTimestamp(destinationUuid, destinationDeviceId, sourceUuid, timestamp);
      cacheMissByNameMeter.mark();
    } else {
      cacheHitByNameMeter.mark();
    }

    return removed;
  }

  public Optional<OutgoingMessageEntity> delete(UUID destinationUuid, long destinationDeviceId, UUID guid) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    Optional<OutgoingMessageEntity> removed = messagesCache.remove(destinationUuid, destinationDeviceId, guid);

    if (removed.isEmpty()) {
      removed = messagesDynamoDb.deleteMessageByDestinationAndGuid(destinationUuid, destinationDeviceId, guid);
      cacheMissByGuidMeter.mark();
    } else {
      cacheHitByGuidMeter.mark();
    }

    return removed;
  }

  public void persistMessages(
      final UUID destinationUuid,
      final long destinationDeviceId,
      final List<Envelope> messages) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesDynamoDb.store(messages, destinationUuid, destinationDeviceId);
    messagesCache.remove(destinationUuid, destinationDeviceId, messages.stream().map(message -> UUID.fromString(message.getServerGuid())).collect(Collectors.toList()));
  }

  public void addMessageAvailabilityListener(
      final UUID destinationUuid,
      final long destinationDeviceId,
      final MessageAvailabilityListener listener) {
    org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.addMessageAvailabilityListener(destinationUuid, destinationDeviceId, listener);
  }

  public void removeMessageAvailabilityListener(final MessageAvailabilityListener listener) {
    messagesCache.removeMessageAvailabilityListener(listener);
  }
}
