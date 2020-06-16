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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.Auth;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.Anonymous;
import org.whispersystems.textsecuregcm.auth.OptionalAccess;
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
import org.whispersystems.textsecuregcm.push.NotPushRegisteredException;
import org.whispersystems.textsecuregcm.push.PushSender;
import org.whispersystems.textsecuregcm.push.ReceiptSender;
import org.whispersystems.textsecuregcm.redis.RedisOperation;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticDevice;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.websocket.WebSocketConnection;

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
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/messages")
public class MessageController {

  private final Logger         logger                       = LoggerFactory.getLogger(MessageController.class);
  private final MetricRegistry metricRegistry               = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          unidentifiedMeter            = metricRegistry.meter(name(getClass(), "delivery", "unidentified"));
  private final Meter          identifiedMeter              = metricRegistry.meter(name(getClass(), "delivery", "identified"  ));
  private final Timer          sendMessageInternalTimer     = metricRegistry.timer(name(getClass(), "sendMessageInternal"));

  private final com.diskuv.communicatorservice.auth.JwtAuthentication      jwtAuthentication;
  private final RateLimiters           rateLimiters;
  private final PushSender             pushSender;
  private final ReceiptSender          receiptSender;
  private final PossiblySyntheticAccountsManager accountsManager;
  private final MessagesManager        messagesManager;
  private final ApnFallbackManager     apnFallbackManager;

  private static final String CONTENT_SIZE_DISTRIBUTION_NAME = name(MessageController.class, "messageContentSize");

  public MessageController(com.diskuv.communicatorservice.auth.JwtAuthentication jwtAuthentication,
                           RateLimiters rateLimiters,
                           PushSender pushSender,
                           ReceiptSender receiptSender,
                           PossiblySyntheticAccountsManager accountsManager,
                           MessagesManager messagesManager,
                           ApnFallbackManager apnFallbackManager)
  {
    this.jwtAuthentication      = jwtAuthentication;
    this.rateLimiters           = rateLimiters;
    this.pushSender             = pushSender;
    this.receiptSender          = receiptSender;
    this.accountsManager        = accountsManager;
    this.messagesManager        = messagesManager;
    this.apnFallbackManager     = apnFallbackManager;
  }

  @Timed
  @Path("/{destination}")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public SendMessageResponse sendMessage(@Auth                                     Account             realSource,
                                         @HeaderParam("Authorization")             String              authorizationHeader,
                                         @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
                                         @HeaderParam("User-Agent")                String userAgent,
                                         @PathParam("destination")                 AmbiguousIdentifier destinationName,
                                         @Valid                                    IncomingMessageList messages)
      throws RateLimitExceededException
  {
    // Unlike Signal, we expect every API to fully authenticate the real source, and edge routers are going to authenticate
    // way before it gets to the Java server. Those edge routers make it possible to stop denial of service.
    // However, it is fine if the effective source ... the source seen by a
    // recipient in the Envelope after we send a message ... is not present as long as the sender shows a valid
    // anonymous key.
    Optional<Account>   source = accessKey.isPresent() ? Optional.empty() : Optional.of(realSource);

    // account authentication (@Auth does it, but we want the outdoors UUID)
    UUID outdoorsUUID = AuthHeaderSupport.validateJwtAndGetOutdoorsUUID(jwtAuthentication, authorizationHeader);

    if (!source.isPresent() && !accessKey.isPresent()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    if (!destinationName.hasUuid()) {
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    if (source.isPresent() && !source.get().isFor(destinationName)) {
      rateLimiters.getMessagesLimiter().validate(source.get().getNumber() + "__" + destinationName);
    }

    if (source.isPresent() && !source.get().isFor(destinationName)) {
      identifiedMeter.mark();
    } else if (!source.isPresent()) {
      unidentifiedMeter.mark();
    }

    try {
      final boolean isSyncMessage = source.isPresent() && source.get().isFor(destinationName);

      PossiblySyntheticAccount destination;

      if (!isSyncMessage) destination = accountsManager.get(destinationName.getUuid());
      else                destination = source.get();

      OptionalAccess.verify(source, accessKey, destination);

      validateCompleteDeviceList(destination, messages.getMessages(), isSyncMessage);
      validateRegistrationIds(destination, messages.getMessages());

      if (destination.getRealAccount().isPresent()) {
        for (IncomingMessage incomingMessage : messages.getMessages()) {
          Optional<? extends PossiblySyntheticDevice> destinationDevice = destination.getDevice(incomingMessage.getDestinationDeviceId());

          if (destinationDevice.isPresent() && destinationDevice.get().getRealDevice().isPresent()) {
            if (!Util.isEmpty(incomingMessage.getContent())) {
              Metrics.summary(CONTENT_SIZE_DISTRIBUTION_NAME, UserAgentTagUtil.getUserAgentTags(userAgent)).record(incomingMessage.getContent().length());
            }

            sendMessage(source, outdoorsUUID, destination.getRealAccount().get(), destinationDevice.get().getRealDevice().get(), messages.getTimestamp(), messages.isOnline(), incomingMessage);
          }
        }
      }

      return new SendMessageResponse(!isSyncMessage && source.isPresent() && source.get().getEnabledDeviceCount() > 1);
    } catch (NoSuchUserException e) {
      throw new WebApplicationException(Response.status(404).build());
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

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public OutgoingMessageEntityList getPendingMessages(@Auth Account account) {
    assert account.getAuthenticatedDevice().isPresent();

    if (!Util.isEmpty(account.getAuthenticatedDevice().get().getApnId())) {
      RedisOperation.unchecked(() -> apnFallbackManager.cancel(account, account.getAuthenticatedDevice().get()));
    }

    return messagesManager.getMessagesForDevice(account.getNumber(),
                                                account.getAuthenticatedDevice().get().getId());
  }

  @Timed
  @DELETE
  @Path("/{source}/{timestamp}")
  public void removePendingMessage(@Auth Account account,
                                   @PathParam("source") String source,
                                   @PathParam("timestamp") long timestamp)
  {
    try {
      UUID.fromString(source);
    } catch (Exception e) {
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
    try {
      WebSocketConnection.messageTime.update(System.currentTimeMillis() - timestamp);

      Optional<OutgoingMessageEntity> message = messagesManager.delete(account.getNumber(),
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
      Optional<OutgoingMessageEntity> message = messagesManager.delete(account.getNumber(),
                                                                       account.getAuthenticatedDevice().get().getId(),
                                                                       uuid);

      message.ifPresent(outgoingMessageEntity -> WebSocketConnection.messageTime.update(System.currentTimeMillis() - outgoingMessageEntity.getTimestamp()));

      if (message.isPresent() && !Util.isEmpty(message.get().getSource()) && message.get().getType() != Envelope.Type.RECEIPT_VALUE) {
        receiptSender.sendReceipt(account, message.get().getSourceUuid().toString(), message.get().getTimestamp());
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
        messageBuilder.setSource("") // WAS: source.get().getNumber()
                      .setSourceUuid(source.get().getUuid().toString())
                      .setSourceDevice((int)source.get().getAuthenticatedDevice().get().getId());
      }

      if (messageBody.isPresent()) {
        messageBuilder.setLegacyMessage(ByteString.copyFrom(messageBody.get()));
      }

      if (messageContent.isPresent()) {
        messageBuilder.setContent(ByteString.copyFrom(messageContent.get()));
      }

      pushSender.sendMessage(destinationAccount, destinationDevice, messageBuilder.build(), online);
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
