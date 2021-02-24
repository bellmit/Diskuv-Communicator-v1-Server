/*
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.controllers;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.Auth;
import io.dropwizard.util.DataSize;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.Anonymous;
import org.whispersystems.textsecuregcm.auth.OptionalAccess;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicMessageRateConfiguration;
import org.whispersystems.textsecuregcm.entities.IncomingMessage;
import org.whispersystems.textsecuregcm.entities.IncomingMessageList;
import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.MismatchedDevices;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntity;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntityList;
import org.whispersystems.textsecuregcm.entities.SendMessageResponse;
import org.whispersystems.textsecuregcm.entities.StaleDevices;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.metrics.UserAgentTagUtil;
import org.whispersystems.textsecuregcm.push.ApnFallbackManager;
import org.whispersystems.textsecuregcm.push.MessageSender;
import org.whispersystems.textsecuregcm.push.NotPushRegisteredException;
import org.whispersystems.textsecuregcm.push.ReceiptSender;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.redis.RedisOperation;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.DynamicConfigurationManager;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticDevice;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.util.ua.UnrecognizedUserAgentException;
import org.whispersystems.textsecuregcm.util.ua.UserAgentUtil;
import org.whispersystems.textsecuregcm.websocket.WebSocketConnection;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/messages")
public class MessageController {

  private final Logger         logger                           = LoggerFactory.getLogger(MessageController.class);
  private final MetricRegistry metricRegistry                   = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          unidentifiedMeter                = metricRegistry.meter(name(getClass(), "delivery", "unidentified"));
  private final Meter          identifiedMeter                  = metricRegistry.meter(name(getClass(), "delivery", "identified"  ));
  private final Meter          rejectOver256kibMessageMeter     = metricRegistry.meter(name(getClass(), "rejectOver256kibMessage"));
  private final Timer          sendMessageInternalTimer         = metricRegistry.timer(name(getClass(), "sendMessageInternal"));
  private final Histogram      outgoingMessageListSizeHistogram = metricRegistry.histogram(name(getClass(), "outgoingMessageListSize"));

  private final com.diskuv.communicatorservice.auth.JwtAuthentication      jwtAuthentication;
  private final RateLimiters                rateLimiters;
  private final MessageSender               messageSender;
  private final ReceiptSender               receiptSender;
  private final PossiblySyntheticAccountsManager             accountsManager;
  private final MessagesManager             messagesManager;
  private final ApnFallbackManager          apnFallbackManager;
  private final DynamicConfigurationManager dynamicConfigurationManager;
  private final FaultTolerantRedisCluster   metricsCluster;
  private final ScheduledExecutorService    receiptExecutorService;

  private final Random random = new Random();

  private static final String SENT_MESSAGE_COUNTER_NAME                          = name(MessageController.class, "sentMessages");
  private static final String REJECT_UNSEALED_SENDER_COUNTER_NAME                = name(MessageController.class, "rejectUnsealedSenderLimit");
  private static final String INTERNATIONAL_UNSEALED_SENDER_COUNTER_NAME         = name(MessageController.class, "internationalUnsealedSender");
  private static final String UNSEALED_SENDER_ACCOUNT_AGE_DISTRIBUTION_NAME      = name(MessageController.class, "unsealedSenderAccountAge");
  private static final String UNSEALED_SENDER_WITHOUT_PUSH_TOKEN_COUNTER_NAME    = name(MessageController.class, "unsealedSenderWithoutPushToken");
  private static final String DECLINED_DELIVERY_COUNTER                          = name(MessageController.class, "declinedDelivery");
  private static final String CONTENT_SIZE_DISTRIBUTION_NAME                     = name(MessageController.class, "messageContentSize");
  private static final String OUTGOING_MESSAGE_LIST_SIZE_BYTES_DISTRIBUTION_NAME = name(MessageController.class, "outgoingMessageListSizeBytes");

  private static final String EPHEMERAL_TAG_NAME      = "ephemeral";
  private static final String SENDER_TYPE_TAG_NAME    = "senderType";
  private static final String SENDER_COUNTRY_TAG_NAME = "senderCountry";

  private static final long MAX_MESSAGE_SIZE = DataSize.kibibytes(256).toBytes();

  private static final String SENT_FIRST_UNSEALED_SENDER_MESSAGE_KEY = "sent_first_unsealed_sender_message";

  public MessageController(com.diskuv.communicatorservice.auth.JwtAuthentication jwtAuthentication,
                           RateLimiters rateLimiters,
                           MessageSender messageSender,
                           ReceiptSender receiptSender,
                           PossiblySyntheticAccountsManager accountsManager,
                           MessagesManager messagesManager,
                           ApnFallbackManager apnFallbackManager,
                           DynamicConfigurationManager dynamicConfigurationManager,
                           FaultTolerantRedisCluster metricsCluster,
                           ScheduledExecutorService receiptExecutorService)
  {
    this.jwtAuthentication           = jwtAuthentication;
    this.rateLimiters                = rateLimiters;
    this.messageSender               = messageSender;
    this.receiptSender               = receiptSender;
    this.accountsManager             = accountsManager;
    this.messagesManager             = messagesManager;
    this.apnFallbackManager          = apnFallbackManager;
    this.dynamicConfigurationManager = dynamicConfigurationManager;
    this.metricsCluster              = metricsCluster;
    this.receiptExecutorService      = receiptExecutorService;
  }

  @Timed
  @Path("/{destination}")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response sendMessage(@Auth                                     Account             realSource,
                              @HeaderParam("Authorization")             String              authorizationHeader,
                              @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
                              @HeaderParam("User-Agent")                String userAgent,
                              @HeaderParam("X-Forwarded-For")           String forwardedFor,
                              @PathParam("destination")                 AmbiguousIdentifier destinationName,
                              @Valid                                    IncomingMessageList messages)
      throws RateLimitExceededException
  {
    // Unlike Signal, we expect every API to fully authenticate the real source, and edge routers are going to authenticate
    // way before it gets to the Java server. Those edge routers make it possible to stop denial of service.
    // However, it is fine if the effective source ... the source seen by a
    // recipient in the Envelope after we send a message ... is not present as long as the sender shows a valid
    // anonymous key.
    final Optional<Account> source = accessKey.isPresent() ? Optional.empty() : Optional.of(realSource);
    if (!destinationName.hasUuid()) {
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    // account authentication (@Auth does it, but we want the outdoors UUID)
    UUID outdoorsUUID = AuthHeaderSupport.validateJwtAndGetOutdoorsUUID(jwtAuthentication, authorizationHeader);

    if (source.isEmpty() && accessKey.isEmpty()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    if (source.isPresent() && !source.get().isFor(destinationName)) {
      assert source.get().getMasterDevice().isPresent();

      final Device masterDevice = source.get().getMasterDevice().get();
      final String senderCountryCode = Util.getCountryCode(source.get().getNumber());

      if (StringUtils.isAllBlank(masterDevice.getApnId(), masterDevice.getVoipApnId(), masterDevice.getGcmId()) || masterDevice.getUninstalledFeedbackTimestamp() > 0) {
        Metrics.counter(UNSEALED_SENDER_WITHOUT_PUSH_TOKEN_COUNTER_NAME).increment();
      }

      RedisOperation.unchecked(() -> {
        metricsCluster.useCluster(connection -> {
          if (connection.sync().pfadd(SENT_FIRST_UNSEALED_SENDER_MESSAGE_KEY, source.get().getUuid().toString()) == 1) {
            final List<Tag> tags = List.of(
                UserAgentTagUtil.getPlatformTag(userAgent),
                Tag.of(SENDER_COUNTRY_TAG_NAME, senderCountryCode));

            final long accountAge = System.currentTimeMillis() - masterDevice.getCreated();

            DistributionSummary.builder(UNSEALED_SENDER_ACCOUNT_AGE_DISTRIBUTION_NAME)
                .tags(tags)
                .publishPercentileHistogram()
                .register(Metrics.globalRegistry)
                .record(accountAge);
          }
        });
      });

      if (dynamicConfigurationManager.getConfiguration().getMessageRateConfiguration().getRateLimitedCountryCodes().contains(senderCountryCode)) {
        try {
          rateLimiters.getUnsealedSenderLimiter().validate(source.get().getUuid().toString(), destinationName.toString());
        } catch (RateLimitExceededException e) {
          Metrics.counter(REJECT_UNSEALED_SENDER_COUNTER_NAME, SENDER_COUNTRY_TAG_NAME, senderCountryCode).increment();

          if (dynamicConfigurationManager.getConfiguration().getMessageRateConfiguration().isEnforceUnsealedSenderRateLimit()) {
            logger.debug("Rejected unsealed sender limit from: {}", source.get().getNumber());
            throw e;
          } else {
            logger.debug("Would reject unsealed sender limit from: {}", source.get().getNumber());
          }
        }
      }
    }

    final String senderType;

    if (source.isPresent() && !source.get().isFor(destinationName)) {
      identifiedMeter.mark();
      senderType = "identified";
    } else if (source.isEmpty()) {
      unidentifiedMeter.mark();
      senderType = "unidentified";
    } else {
      senderType = "self";
    }

    for (final IncomingMessage message : messages.getMessages()) {
      int contentLength = 0;

      if (!Util.isEmpty(message.getContent())) {
        contentLength += message.getContent().length();
      }

      if (!Util.isEmpty(message.getBody())) {
        contentLength += message.getBody().length();
      }

      Metrics.summary(CONTENT_SIZE_DISTRIBUTION_NAME, UserAgentTagUtil.getUserAgentTags(userAgent)).record(contentLength);

      if (contentLength > MAX_MESSAGE_SIZE) {
        rejectOver256kibMessageMeter.mark();
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
      }
    }

    try {
      boolean isSyncMessage = source.isPresent() && source.get().isFor(destinationName);

      Optional<? extends PossiblySyntheticAccount> destination;

      if (!isSyncMessage) destination = Optional.of(accountsManager.get(destinationName.getUuid()));
      else                destination = source;

      OptionalAccess.verify(source, accessKey, destination.get());

      if (source.isPresent() && !source.get().isFor(destinationName)) {
        rateLimiters.getMessagesLimiter().validate(source.get().getUuid() + "__" + destination.get().getUuid());
      }

      validateCompleteDeviceList(destination.get(), messages.getMessages(), isSyncMessage);
      validateRegistrationIds(destination.get(), messages.getMessages());

      // iOS versions prior to 5.5.0.7 send `online` on  IncomingMessageList.message, rather on the top-level entity.
      // This causes some odd client behaviors, such as persisted typing indicators, so we have a temporary
      // server-side adaptation.
      final boolean online = messages.getMessages()
          .stream()
          .findFirst()
          .map(IncomingMessage::isOnline)
          .orElse(messages.isOnline());

      final List<Tag> tags = List.of(UserAgentTagUtil.getPlatformTag(userAgent),
                                     Tag.of(EPHEMERAL_TAG_NAME, String.valueOf(online)),
                                     Tag.of(SENDER_TYPE_TAG_NAME, senderType));

      for (IncomingMessage incomingMessage : messages.getMessages()) {
        // Don't send anything if a synthetic device
        Optional<? extends PossiblySyntheticDevice> possibleDestinationDevice = destination.get().getDevice(incomingMessage.getDestinationDeviceId());
        if (possibleDestinationDevice.isEmpty() || possibleDestinationDevice.get().getRealDevice().isEmpty()) {
          continue;
        }

        Optional<Device> destinationDevice = possibleDestinationDevice.get().getRealDevice();

        if (destinationDevice.isPresent()) {
          Metrics.counter(SENT_MESSAGE_COUNTER_NAME, tags).increment();
          sendMessage(source, outdoorsUUID, destination.get().getRealAccount().get(), destinationDevice.get(), messages.getTimestamp(), online, incomingMessage);
        }
      }

      return Response.ok(new SendMessageResponse(!isSyncMessage && source.isPresent() && source.get().getEnabledDeviceCount() > 1)).build();
    } catch (NoSuchUserException e) {
      // We should not leak that a user does not exist!
      return Response.ok(new SendMessageResponse(false)).build();
    } catch (MismatchedDevicesException e) {
      throw new WebApplicationException(Response.status(409)
              .type(MediaType.APPLICATION_JSON_TYPE)
              .entity(new MismatchedDevices(e.getMissingDevices(),
                      e.getExtraDevices()))
              .build());
    } catch (StaleDevicesException e) {
      throw new WebApplicationException(Response.status(410)
              .type(MediaType.APPLICATION_JSON)
              .entity(new StaleDevices(e.getStaleDevices()))
              .build());
    }
  }

  private Response declineDelivery(final IncomingMessageList messages, final Account source, final Account destination) {
    Metrics.counter(DECLINED_DELIVERY_COUNTER, SENDER_COUNTRY_TAG_NAME, Util.getCountryCode(source.getNumber())).increment();

    final DynamicMessageRateConfiguration messageRateConfiguration = dynamicConfigurationManager.getConfiguration().getMessageRateConfiguration();

    {
      final long timestamp = System.currentTimeMillis();

      for (final IncomingMessage message : messages.getMessages()) {
        final long jitterNanos = random.nextInt((int) messageRateConfiguration.getReceiptDelayJitter().toNanos());
        final Duration receiptDelay = messageRateConfiguration.getReceiptDelay().plusNanos(jitterNanos);

        if (random.nextDouble() <= messageRateConfiguration.getReceiptProbability()) {
          receiptExecutorService.schedule(() -> {
            try {
              receiptSender.sendReceipt(destination, source.getNumber(), timestamp);
            } catch (final NoSuchUserException ignored) {
            }
          }, receiptDelay.toMillis(), TimeUnit.MILLISECONDS);
        }
      }
    }

    {
      Duration responseDelay = Duration.ZERO;

      for (int i = 0; i < messages.getMessages().size(); i++) {
        final long jitterNanos = random.nextInt((int) messageRateConfiguration.getResponseDelayJitter().toNanos());

        responseDelay = responseDelay.plus(
            messageRateConfiguration.getResponseDelay()).plusNanos(jitterNanos);
      }

      Util.sleep(responseDelay.toMillis());
    }

    return Response.ok(new SendMessageResponse(source.getEnabledDeviceCount() > 1)).build();
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public OutgoingMessageEntityList getPendingMessages(@Auth Account account, @HeaderParam("User-Agent") String userAgent) {
    assert account.getAuthenticatedDevice().isPresent();

    if (!Util.isEmpty(account.getAuthenticatedDevice().get().getApnId())) {
      RedisOperation.unchecked(() -> apnFallbackManager.cancel(account, account.getAuthenticatedDevice().get()));
    }

    final OutgoingMessageEntityList outgoingMessages = messagesManager.getMessagesForDevice(
        account.getUuid(),
        account.getAuthenticatedDevice().get().getId(),
        userAgent,
        false);

    outgoingMessageListSizeHistogram.update(outgoingMessages.getMessages().size());

    {
      String platform;

      try {
        platform = UserAgentUtil.parseUserAgentString(userAgent).getPlatform().name().toLowerCase();
      } catch (final UnrecognizedUserAgentException ignored) {
        platform = "unrecognized";
      }

      Metrics.summary(OUTGOING_MESSAGE_LIST_SIZE_BYTES_DISTRIBUTION_NAME, "platform", platform).record(estimateMessageListSizeBytes(outgoingMessages));
    }

    return outgoingMessages;
  }

  private static long estimateMessageListSizeBytes(final OutgoingMessageEntityList messageList) {
    long size = 0;

    for (final OutgoingMessageEntity message : messageList.getMessages()) {
      size += message.getContent() == null      ? 0 : message.getContent().length;
      size += message.getMessage() == null      ? 0 : message.getMessage().length;
      size += Util.isEmpty(message.getSource()) ? 0 : message.getSource().length();
      size += message.getSourceUuid() == null   ? 0 : 36;
      size += Util.isEmpty(message.getRelay())  ? 0 : message.getRelay().length();
    }

    return size;
  }

  @Timed
  @DELETE
  @Path("/{source}/{timestamp}")
  public void removePendingMessage(@Auth Account account,
                                   @PathParam("source") UUID source,
                                   @PathParam("timestamp") long timestamp)
  {
    try {
      WebSocketConnection.recordMessageDeliveryDuration(timestamp, account.getAuthenticatedDevice().get());
      Optional<OutgoingMessageEntity> message = messagesManager.delete(
          account.getUuid(),
                                                                       account.getAuthenticatedDevice().get().getId(),
                                                                       source, timestamp);

      if (message.isPresent() && message.get().getType() != Envelope.Type.RECEIPT_VALUE) {
        receiptSender.sendReceipt(account,
                                  message.get().getSourceUuid().toString(),
                                  message.get().getTimestamp());
      }
    } catch (NoSuchUserException e) {
      logger.warn("Sending delivery receipt", e);
    }
  }

  @Timed
  @DELETE
  @Path("/uuid/{uuid}")
  public void removePendingMessage(@Auth Account account, @PathParam("uuid") UUID uuid) {
    try {
      Optional<OutgoingMessageEntity> message = messagesManager.delete(
          account.getUuid(),
                                                                       account.getAuthenticatedDevice().get().getId(),
                                                                       uuid);

      if (message.isPresent()) {
        WebSocketConnection.recordMessageDeliveryDuration(message.get().getTimestamp(), account.getAuthenticatedDevice().get());
        if (message.get().getSourceUuid() != null && message.get().getType() != Envelope.Type.RECEIPT_VALUE) {
          receiptSender.sendReceipt(account, message.get().getSourceUuid().toString(), message.get().getTimestamp());
        }
      }

    } catch (NoSuchUserException e) {
      logger.warn("Sending delivery receipt", e);
    }
  }

  private void sendMessage(Optional<Account> source,
                           UUID sourceOutdoorsUUID,
                           Account destinationAccount,
                           Device destinationDevice,
                           long timestamp,
                           boolean online,
                           IncomingMessage incomingMessage)
      throws NoSuchUserException
  {
    try (final Timer.Context ignored = sendMessageInternalTimer.time()) {
      Optional<byte[]> messageBody    = getMessageBody(incomingMessage);
      Optional<byte[]> messageContent = getMessageContent(incomingMessage);
      Envelope.Builder messageBuilder = Envelope.newBuilder();

      messageBuilder.setType(Envelope.Type.valueOf(incomingMessage.getType()))
                    .setTimestamp(timestamp == 0 ? System.currentTimeMillis() : timestamp)
                    .setServerTimestamp(System.currentTimeMillis())
                    .setServerOutdoorsSourceUuid(sourceOutdoorsUUID.toString());

      if (source.isPresent()) {
        // Contact by email address. Not phone number.
        messageBuilder // WAS: .setSource(source.get().getNumber())
                      .setSourceUuid(source.get().getUuid().toString())
                      .setSourceDevice((int)source.get().getAuthenticatedDevice().get().getId());
      }

      if (messageBody.isPresent()) {
        messageBuilder.setLegacyMessage(ByteString.copyFrom(messageBody.get()));
      }

      if (messageContent.isPresent()) {
        messageBuilder.setContent(ByteString.copyFrom(messageContent.get()));
      }

      messageSender.sendMessage(destinationAccount, destinationDevice, messageBuilder.build(), online);
    } catch (NotPushRegisteredException e) {
      if (destinationDevice.isMaster()) throw new NoSuchUserException(e);
      else                              logger.debug("Not registered", e);
    }
  }

  private void validateRegistrationIds(PossiblySyntheticAccount account, List<IncomingMessage> messages)
      throws StaleDevicesException
  {
    List<Long> staleDevices = new LinkedList<>();

    for (IncomingMessage message : messages) {
      Optional<? extends PossiblySyntheticDevice> device = account.getDevice(message.getDestinationDeviceId());

      if (device.isPresent() &&
          message.getDestinationRegistrationId() > 0 &&
          message.getDestinationRegistrationId() != device.get().getRegistrationId())
      {
        staleDevices.add(device.get().getId());
      }
    }

    if (!staleDevices.isEmpty()) {
      throw new StaleDevicesException(staleDevices);
    }
  }

  private void validateCompleteDeviceList(PossiblySyntheticAccount account,
                                          List<IncomingMessage> messages,
                                          boolean isSyncMessage)
      throws MismatchedDevicesException
  {
    Set<Long> messageDeviceIds = new HashSet<>();
    Set<Long> accountDeviceIds = new HashSet<>();

    List<Long> missingDeviceIds = new LinkedList<>();
    List<Long> extraDeviceIds   = new LinkedList<>();

    for (IncomingMessage message : messages) {
      messageDeviceIds.add(message.getDestinationDeviceId());
    }

    for (PossiblySyntheticDevice device : account.getDevices()) {
      if (device.isEnabled() &&
          !(isSyncMessage && device.getId() == account.getAuthenticatedDevice().get().getId()))
      {
        accountDeviceIds.add(device.getId());

        if (!messageDeviceIds.contains(device.getId())) {
          missingDeviceIds.add(device.getId());
        }
      }
    }

    for (IncomingMessage message : messages) {
      if (!accountDeviceIds.contains(message.getDestinationDeviceId())) {
        extraDeviceIds.add(message.getDestinationDeviceId());
      }
    }

    if (!missingDeviceIds.isEmpty() || !extraDeviceIds.isEmpty()) {
      throw new MismatchedDevicesException(missingDeviceIds, extraDeviceIds);
    }
  }

  private Optional<byte[]> getMessageBody(IncomingMessage message) {
    if (Util.isEmpty(message.getBody())) return Optional.empty();

    try {
      return Optional.of(Base64.decode(message.getBody()));
    } catch (IOException ioe) {
      logger.debug("Bad B64", ioe);
      return Optional.empty();
    }
  }

  private Optional<byte[]> getMessageContent(IncomingMessage message) {
    if (Util.isEmpty(message.getContent())) return Optional.empty();

    try {
      return Optional.of(Base64.decode(message.getContent()));
    } catch (IOException ioe) {
      logger.debug("Bad B64", ioe);
      return Optional.empty();
    }
  }
}
