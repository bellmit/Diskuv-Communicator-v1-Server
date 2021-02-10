package org.whispersystems.textsecuregcm.storage;


import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntity;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntityList;
import org.whispersystems.textsecuregcm.experiment.ExperimentEnrollmentManager;
import org.whispersystems.textsecuregcm.metrics.PushLatencyManager;
import org.whispersystems.textsecuregcm.redis.RedisOperation;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

public class MessagesManager {

  private static final String DISABLE_RDS_EXPERIMENT = "messages_disable_rds";

  private static final MetricRegistry metricRegistry       = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private static final Meter          cacheHitByNameMeter  = metricRegistry.meter(name(MessagesManager.class, "cacheHitByName" ));
  private static final Meter          cacheMissByNameMeter = metricRegistry.meter(name(MessagesManager.class, "cacheMissByName"));
  private static final Meter          cacheHitByGuidMeter  = metricRegistry.meter(name(MessagesManager.class, "cacheHitByGuid" ));
  private static final Meter          cacheMissByGuidMeter = metricRegistry.meter(name(MessagesManager.class, "cacheMissByGuid"));

  private final Messages messages;
  private final MessagesDynamoDb messagesDynamoDb;
  private final MessagesCache messagesCache;
  private final PushLatencyManager pushLatencyManager;
  private final ExperimentEnrollmentManager experimentEnrollmentManager;

  public MessagesManager(Messages messages, MessagesDynamoDb messagesDynamoDb, MessagesCache messagesCache, PushLatencyManager pushLatencyManager, ExperimentEnrollmentManager experimentEnrollmentManager) {
    this.messages = messages;
    this.messagesDynamoDb = messagesDynamoDb;
    this.messagesCache = messagesCache;
    this.pushLatencyManager = pushLatencyManager;
    this.experimentEnrollmentManager = experimentEnrollmentManager;
  }

  public void insert(UUID destinationUuid, long destinationDevice, Envelope message) {
    DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.insert(UUID.randomUUID(), destinationUuid, destinationDevice, message);
  }

  public void insertEphemeral(final UUID destinationUuid, final long destinationDevice, final Envelope message) {
    DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    messagesCache.insertEphemeral(destinationUuid, destinationDevice, message);
  }

  public Optional<Envelope> takeEphemeralMessage(final UUID destinationUuid, final long destinationDevice) {
    DiskuvUuidUtil.verifyDiskuvUuid(destinationUuid.toString());
    return messagesCache.takeEphemeralMessage(destinationUuid, destinationDevice);
  }

  public boolean hasCachedMessages(final UUID destinationUuid, final long destinationDevice) {
    return messagesCache.hasMessages(destinationUuid, destinationDevice);
  }

  public OutgoingMessageEntityList getMessagesForDevice(String destination, UUID destinationUuid, long destinationDevice, final String userAgent, final boolean cachedMessagesOnly) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(destinationUuid.toString().equals(destination));

    RedisOperation.unchecked(() -> pushLatencyManager.recordQueueRead(destinationUuid, destinationDevice, userAgent));

    List<OutgoingMessageEntity> messageList = new ArrayList<>();

    if (!cachedMessagesOnly && !experimentEnrollmentManager.isEnrolled(destinationUuid, DISABLE_RDS_EXPERIMENT)) {
      messageList.addAll(messages.load(destination, destinationDevice));
    }

    if (messageList.size() < Messages.RESULT_SET_CHUNK_SIZE && !cachedMessagesOnly) {
      messageList.addAll(messagesDynamoDb.load(destinationUuid, destinationDevice, Messages.RESULT_SET_CHUNK_SIZE - messageList.size()));
    }

    if (messageList.size() < Messages.RESULT_SET_CHUNK_SIZE) {
      messageList.addAll(messagesCache.get(destinationUuid, destinationDevice, Messages.RESULT_SET_CHUNK_SIZE - messageList.size()));
    }

    return new OutgoingMessageEntityList(messageList, messageList.size() >= Messages.RESULT_SET_CHUNK_SIZE);
  }

  public void clear(String destination, UUID destinationUuid) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(destinationUuid.toString().equals(destination));

    // TODO Remove this null check in a fully-UUID-ified world
    if (destinationUuid != null) {
      messagesCache.clear(destinationUuid);
      messagesDynamoDb.deleteAllMessagesForAccount(destinationUuid);
      if (!experimentEnrollmentManager.isEnrolled(destinationUuid, DISABLE_RDS_EXPERIMENT)) {
        messages.clear(destination);
      }
    } else {
      messages.clear(destination);
    }
  }

  public void clear(String destination, UUID destinationUuid, long deviceId) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(destinationUuid.toString().equals(destination));
    messagesCache.clear(destinationUuid, deviceId);
    messagesDynamoDb.deleteAllMessagesForDevice(destinationUuid, deviceId);
    if (!experimentEnrollmentManager.isEnrolled(destinationUuid, DISABLE_RDS_EXPERIMENT)) {
      messages.clear(destination, deviceId);
    }
  }

  public Optional<OutgoingMessageEntity> delete(String destination, UUID destinationUuid, long destinationDevice, UUID sourceUuid, long timestamp) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(destinationUuid.toString().equals(destination));
    Optional<OutgoingMessageEntity> removed = messagesCache.remove(destinationUuid, destinationDevice, sourceUuid.toString(), timestamp);

    if (removed.isEmpty()) {
      removed = messagesDynamoDb.deleteMessageByDestinationAndSourceUuidAndTimestamp(destinationUuid, destinationDevice, sourceUuid, timestamp);
      if (removed.isEmpty() && !experimentEnrollmentManager.isEnrolled(destinationUuid, DISABLE_RDS_EXPERIMENT)) {
        removed = messages.remove(destination, destinationDevice, sourceUuid.toString(), timestamp);
      }
      cacheMissByNameMeter.mark();
    } else {
      cacheHitByNameMeter.mark();
    }

    return removed;
  }

  public Optional<OutgoingMessageEntity> delete(String destination, UUID destinationUuid, long deviceId, UUID guid) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(destinationUuid.toString().equals(destination));

    Optional<OutgoingMessageEntity> removed = messagesCache.remove(destinationUuid, deviceId, guid);

    if (removed.isEmpty()) {
      removed = messagesDynamoDb.deleteMessageByDestinationAndGuid(destinationUuid, deviceId, guid);
      if (removed.isEmpty() && !experimentEnrollmentManager.isEnrolled(destinationUuid, DISABLE_RDS_EXPERIMENT)) {
        removed = messages.remove(destination, guid);
      }
      cacheMissByGuidMeter.mark();
    } else {
      cacheHitByGuidMeter.mark();
    }

    return removed;
  }

  @Deprecated
  public void delete(String destination, long id) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    messages.remove(destination, id);
  }

  public void persistMessages(final String destination, final UUID destinationUuid, final long destinationDeviceId, final List<Envelope> messages) {
    DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(destinationUuid.toString().equals(destination));
    messagesDynamoDb.store(messages, destinationUuid, destinationDeviceId);
    messagesCache.remove(destinationUuid, destinationDeviceId, messages.stream().map(message -> UUID.fromString(message.getServerGuid())).collect(Collectors.toList()));
  }

  public void addMessageAvailabilityListener(final UUID destinationUuid, final long deviceId, final MessageAvailabilityListener listener) {
    messagesCache.addMessageAvailabilityListener(destinationUuid, deviceId, listener);
  }

  public void removeMessageAvailabilityListener(final MessageAvailabilityListener listener) {
    messagesCache.removeMessageAvailabilityListener(listener);
  }
}
