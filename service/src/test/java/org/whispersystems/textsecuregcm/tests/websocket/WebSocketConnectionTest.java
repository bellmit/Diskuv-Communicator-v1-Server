package org.whispersystems.textsecuregcm.tests.websocket;

import com.google.protobuf.ByteString;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.whispersystems.textsecuregcm.auth.DiskuvAccountAuthenticator;
import com.diskuv.communicatorservice.auth.DiskuvDeviceCredentials;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntity;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntityList;
import org.whispersystems.textsecuregcm.push.ApnFallbackManager;
import org.whispersystems.textsecuregcm.push.PushSender;
import org.whispersystems.textsecuregcm.push.ReceiptSender;
import org.whispersystems.textsecuregcm.push.WebsocketSender;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.PubSubManager;
import org.whispersystems.textsecuregcm.storage.PubSubProtos;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.websocket.AuthenticatedConnectListener;
import org.whispersystems.textsecuregcm.websocket.WebSocketAccountAuthenticator;
import org.whispersystems.textsecuregcm.websocket.WebSocketConnection;
import org.whispersystems.textsecuregcm.websocket.WebsocketAddress;
import org.whispersystems.websocket.WebSocketClient;
import org.whispersystems.websocket.auth.WebSocketAuthenticator.AuthenticationResult;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;
import org.whispersystems.websocket.session.WebSocketSessionContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.*;

public class WebSocketConnectionTest {

  private static final byte[] VALID_PASSWORD      = new byte[] {1, 2, 3};
  private static final byte[] INVALID_PASSWORD    = new byte[] {4, 5, 6};

  private static final String VALID_JWT_TOKEN     = "quality";
  private static final String INVALID_JWT_TOKEN   = "sketchy";

  private static final UUID VALID_ACCOUNT         = UUID.randomUUID();
  private static final UUID INVALID_ACCOUNT       = UUID.randomUUID();

  private static final long VALID_DEVICE_ID_NUM   = 1;
  private static final long INVALID_DEVICE_ID_NUM = 2;
  private static final String VALID_DEVICE_ID     = Long.toString(VALID_DEVICE_ID_NUM);
  private static final String INVALID_DEVICE_ID   = Long.toString(INVALID_DEVICE_ID_NUM);

  private static final DiskuvAccountAuthenticator accountAuthenticator = mock(DiskuvAccountAuthenticator.class);
  private static final AccountsManager      accountsManager      = mock(AccountsManager.class);
  private static final PubSubManager        pubSubManager        = mock(PubSubManager.class       );
  private static final Account              account              = mock(Account.class             );
  private static final Device               device               = mock(Device.class              );
  private static final UpgradeRequest       upgradeRequest       = mock(UpgradeRequest.class      );
  private static final PushSender           pushSender           = mock(PushSender.class);
  private static final ReceiptSender        receiptSender        = mock(ReceiptSender.class);
  private static final ApnFallbackManager   apnFallbackManager = mock(ApnFallbackManager.class);

  @Test
  public void testCredentials() throws Exception {
    MessagesManager               storedMessages         = mock(MessagesManager.class);
    WebSocketAccountAuthenticator webSocketAuthenticator = new WebSocketAccountAuthenticator(accountAuthenticator);
    AuthenticatedConnectListener  connectListener        = new AuthenticatedConnectListener(pushSender, receiptSender, storedMessages, pubSubManager, apnFallbackManager);
    WebSocketSessionContext       sessionContext         = mock(WebSocketSessionContext.class);

    when(accountAuthenticator.authenticate(eq(new DiskuvDeviceCredentials(VALID_JWT_TOKEN, VALID_ACCOUNT, VALID_DEVICE_ID_NUM, VALID_PASSWORD))))
        .thenReturn(Optional.of(account));

    when(accountAuthenticator.authenticate(eq(new DiskuvDeviceCredentials(INVALID_JWT_TOKEN, INVALID_ACCOUNT, INVALID_DEVICE_ID_NUM, INVALID_PASSWORD))))
        .thenReturn(Optional.<Account>empty());

    when(account.getAuthenticatedDevice()).thenReturn(Optional.of(device));

    when(upgradeRequest.getParameterMap()).thenReturn(new HashMap<String, List<String>>() {{
      put("device-id", new LinkedList<String>() {{add(VALID_DEVICE_ID);}});
      put("device-password", new LinkedList<String>() {{add(Base64.encodeBytes(VALID_PASSWORD, Base64.URL_SAFE));}});
      put("jwt-token", new LinkedList<String>() {{add(VALID_JWT_TOKEN);}});
      put("account-id", new LinkedList<String>() {{add(VALID_ACCOUNT.toString());}});
    }});

    AuthenticationResult<Account> account = webSocketAuthenticator.authenticate(upgradeRequest);
    when(sessionContext.getAuthenticated(Account.class)).thenReturn(account.getUser().orElse(null));

    connectListener.onWebSocketConnect(sessionContext);

    verify(sessionContext).addListener(any(WebSocketSessionContext.WebSocketEventListener.class));

    // --------------------
    // invalid password

    when(upgradeRequest.getParameterMap()).thenReturn(new HashMap<String, List<String>>() {{
      put("device-id", new LinkedList<String>() {{add(INVALID_DEVICE_ID);}});
      put("device-password", new LinkedList<String>() {{add(Base64.encodeBytes(INVALID_PASSWORD, Base64.URL_SAFE));}});
      put("jwt-token", new LinkedList<String>() {{add(INVALID_JWT_TOKEN);}});
      put("account-id", new LinkedList<String>() {{add(INVALID_ACCOUNT.toString());}});
    }});

    account = webSocketAuthenticator.authenticate(upgradeRequest);
    assertFalse(account.getUser().isPresent());
    assertTrue(account.isRequired());

    // --------------------
    // invalid password base64 encoding

    when(upgradeRequest.getParameterMap()).thenReturn(new HashMap<String, List<String>>() {{
      put("device-id", new LinkedList<String>() {{add(VALID_DEVICE_ID);}});
      put("device-password", new LinkedList<String>() {{add("!!bad base64 encoding!!");}});
      put("jwt-token", new LinkedList<String>() {{add(VALID_JWT_TOKEN);}});
      put("account-id", new LinkedList<String>() {{add(VALID_ACCOUNT.toString());}});
    }});

    account = webSocketAuthenticator.authenticate(upgradeRequest);
    assertFalse(account.getUser().isPresent());
    assertTrue(account.isRequired());

    // --------------------
    // invalid password base64 url-safe encoding

    when(upgradeRequest.getParameterMap()).thenReturn(new HashMap<String, List<String>>() {{
      put("device-id", new LinkedList<String>() {{add(VALID_DEVICE_ID);}});
      put("device-password", new LinkedList<String>() {{add("hQUjeNfICbyRWzjdy5+Z6g==");}});
      put("jwt-token", new LinkedList<String>() {{add(VALID_JWT_TOKEN);}});
      put("account-id", new LinkedList<String>() {{add(VALID_ACCOUNT.toString());}});
    }});

    account = webSocketAuthenticator.authenticate(upgradeRequest);
    assertFalse(account.getUser().isPresent());
    assertTrue(account.isRequired());

    // --------------------
    // invalid device id encoding

    when(upgradeRequest.getParameterMap()).thenReturn(new HashMap<String, List<String>>() {{
      put("device-id", new LinkedList<String>() {{add(VALID_DEVICE_ID + " not a number");}});
      put("device-password", new LinkedList<String>() {{add(Base64.encodeBytes(VALID_PASSWORD, Base64.URL_SAFE));}});
      put("jwt-token", new LinkedList<String>() {{add(VALID_JWT_TOKEN);}});
      put("account-id", new LinkedList<String>() {{add(VALID_ACCOUNT.toString());}});
    }});

    account = webSocketAuthenticator.authenticate(upgradeRequest);
    assertFalse(account.getUser().isPresent());
    assertTrue(account.isRequired());
  }

  @Test
  public void testOpen() throws Exception {
    MessagesManager storedMessages = mock(MessagesManager.class);

    UUID accountUuid   = UUID.randomUUID();
    UUID senderOneUuid = UUID.randomUUID();
    UUID senderTwoUuid = UUID.randomUUID();

    List<OutgoingMessageEntity> outgoingMessages = new LinkedList<OutgoingMessageEntity> () {{
      add(createMessage(1L, false, "sender1", senderOneUuid, 1111, false, "first"));
      add(createMessage(2L, false, "sender1", senderOneUuid, 2222, false, "second"));
      add(createMessage(3L, false, "sender2", senderTwoUuid, 3333, false, "third"));
    }};

    OutgoingMessageEntityList outgoingMessagesList = new OutgoingMessageEntityList(outgoingMessages, false);

    when(device.getId()).thenReturn(2L);
    when(device.getSignalingKey()).thenReturn(Base64.encodeBytes(new byte[52]));

    when(account.getAuthenticatedDevice()).thenReturn(Optional.of(device));
    when(account.getNumber()).thenReturn(UUID_ALICE_STRING);
    when(account.getUuid()).thenReturn(accountUuid);

    final Device sender1device = mock(Device.class);

    Set<Device> sender1devices = new HashSet<>() {{
      add(sender1device);
    }};

    Account sender1 = mock(Account.class);
    when(sender1.getDevices()).thenReturn(sender1devices);

    when(accountsManager.get(senderOneUuid)).thenReturn(Optional.of(sender1));
    when(accountsManager.get(senderTwoUuid)).thenReturn(Optional.empty());

    when(storedMessages.getMessagesForDevice(account.getNumber(), account.getUuid(), device.getId()))
        .thenReturn(outgoingMessagesList);

    final List<CompletableFuture<WebSocketResponseMessage>> futures = new LinkedList<>();
    final WebSocketClient                                   client  = mock(WebSocketClient.class);

    when(client.sendRequest(eq("PUT"), eq("/api/v1/message"), ArgumentMatchers.nullable(List.class), ArgumentMatchers.<Optional<byte[]>>any()))
        .thenAnswer(new Answer<CompletableFuture<WebSocketResponseMessage>>() {
          @Override
          public CompletableFuture<WebSocketResponseMessage> answer(InvocationOnMock invocationOnMock) throws Throwable {
            CompletableFuture<WebSocketResponseMessage> future = new CompletableFuture<>();
            futures.add(future);
            return future;
          }
        });

    WebsocketAddress websocketAddress = new WebsocketAddress(account.getNumber(), device.getId());
    WebSocketConnection connection = new WebSocketConnection(pushSender, receiptSender, storedMessages,
                                                             account, device, client, "someid");

    connection.onDispatchSubscribed(websocketAddress.serialize());
    verify(client, times(3)).sendRequest(eq("PUT"), eq("/api/v1/message"), ArgumentMatchers.nullable(List.class), ArgumentMatchers.<Optional<byte[]>>any());

    assertTrue(futures.size() == 3);

    WebSocketResponseMessage response = mock(WebSocketResponseMessage.class);
    when(response.getStatus()).thenReturn(200);
    futures.get(1).complete(response);

    futures.get(0).completeExceptionally(new IOException());
    futures.get(2).completeExceptionally(new IOException());

    verify(storedMessages, times(1)).delete(eq(account.getNumber()), eq(accountUuid), eq(2L), eq(2L), eq(false));
    verify(receiptSender, times(1)).sendReceipt(eq(account), eq(senderOneUuid.toString()), eq(2222L));

    connection.onDispatchUnsubscribed(websocketAddress.serialize());
    verify(client).close(anyInt(), anyString());
  }

  @Test
  public void testOnlineSend() throws Exception {
    MessagesManager storedMessages = mock(MessagesManager.class);
    WebsocketSender websocketSender = mock(WebsocketSender.class);

    when(pushSender.getWebSocketSender()).thenReturn(websocketSender);

    Envelope firstMessage = Envelope.newBuilder()
                                    .setLegacyMessage(ByteString.copyFrom("first".getBytes()))
                                    .setSource("")
                                    .setSourceUuid(UUID_ALICE_STRING)
                                    .setTimestamp(System.currentTimeMillis())
                                    .setSourceDevice(1)
                                    .setType(Envelope.Type.CIPHERTEXT)
                                    .build();

    Envelope secondMessage = Envelope.newBuilder()
                                     .setLegacyMessage(ByteString.copyFrom("second".getBytes()))
                                     .setSource("")
                                     .setSourceUuid(UUID_BOB_STRING)
                                     .setTimestamp(System.currentTimeMillis())
                                     .setSourceDevice(2)
                                     .setType(Envelope.Type.CIPHERTEXT)
                                     .build();

    List<OutgoingMessageEntity> pendingMessages     = new LinkedList<>();
    OutgoingMessageEntityList   pendingMessagesList = new OutgoingMessageEntityList(pendingMessages, false);

    when(device.getId()).thenReturn(2L);
    when(device.getSignalingKey()).thenReturn(Base64.encodeBytes(new byte[52]));

    when(account.getAuthenticatedDevice()).thenReturn(Optional.of(device));
    when(account.getNumber()).thenReturn(UUID_ALICE_STRING);
    when(account.getUuid()).thenReturn(UUID_ALICE);

    final Device sender1device = mock(Device.class);

    Set<Device> sender1devices = new HashSet<Device>() {{
      add(sender1device);
    }};

    Account sender1 = mock(Account.class);
    when(sender1.getDevices()).thenReturn(sender1devices);

    when(accountsManager.get(UUID_ALICE)).thenReturn(Optional.of(sender1));
    when(accountsManager.get(UUID_BOB)).thenReturn(Optional.<Account>empty());

    when(storedMessages.getMessagesForDevice(account.getNumber(), account.getUuid(), device.getId()))
        .thenReturn(pendingMessagesList);

    final List<CompletableFuture<WebSocketResponseMessage>> futures = new LinkedList<>();
    final WebSocketClient                                   client  = mock(WebSocketClient.class);

    when(client.sendRequest(eq("PUT"), eq("/api/v1/message"), ArgumentMatchers.nullable(List.class), ArgumentMatchers.<Optional<byte[]>>any()))
        .thenAnswer(new Answer<CompletableFuture<WebSocketResponseMessage>>() {
          @Override
          public CompletableFuture<WebSocketResponseMessage> answer(InvocationOnMock invocationOnMock) throws Throwable {
            CompletableFuture<WebSocketResponseMessage> future = new CompletableFuture<>();
            futures.add(future);
            return future;
          }
        });

    WebsocketAddress websocketAddress = new WebsocketAddress(account.getNumber(), device.getId());
    WebSocketConnection connection = new WebSocketConnection(pushSender, receiptSender, storedMessages,
                                                             account, device, client, "anotherid");

    connection.onDispatchSubscribed(websocketAddress.serialize());
    connection.onDispatchMessage(websocketAddress.serialize(), PubSubProtos.PubSubMessage.newBuilder()
                                                                                         .setType(PubSubProtos.PubSubMessage.Type.DELIVER)
                                                                                         .setContent(ByteString.copyFrom(firstMessage.toByteArray()))
                                                                                         .build().toByteArray());

    connection.onDispatchMessage(websocketAddress.serialize(), PubSubProtos.PubSubMessage.newBuilder()
                                                                                         .setType(PubSubProtos.PubSubMessage.Type.DELIVER)
                                                                                         .setContent(ByteString.copyFrom(secondMessage.toByteArray()))
                                                                                         .build().toByteArray());

    verify(client, times(2)).sendRequest(eq("PUT"), eq("/api/v1/message"), ArgumentMatchers.nullable(List.class), ArgumentMatchers.<Optional<byte[]>>any());

    assertEquals(futures.size(), 2);

    WebSocketResponseMessage response = mock(WebSocketResponseMessage.class);
    when(response.getStatus()).thenReturn(200);
    futures.get(1).complete(response);
    futures.get(0).completeExceptionally(new IOException());

    verify(receiptSender, times(1)).sendReceipt(eq(account), eq(UUID_BOB_STRING), eq(secondMessage.getTimestamp()));
    verify(websocketSender, times(1)).queueMessage(eq(account), eq(device), any(Envelope.class));
    verify(pushSender, times(1)).sendQueuedNotification(eq(account), eq(device));

    connection.onDispatchUnsubscribed(websocketAddress.serialize());
    verify(client).close(anyInt(), anyString());
  }

  @Test
  public void testPendingSend() throws Exception {
    MessagesManager storedMessages  = mock(MessagesManager.class);
    WebsocketSender websocketSender = mock(WebsocketSender.class);

    reset(websocketSender);
    reset(pushSender);

    when(pushSender.getWebSocketSender()).thenReturn(websocketSender);

    final Envelope firstMessage = Envelope.newBuilder()
                                    .setLegacyMessage(ByteString.copyFrom("first".getBytes()))
                                    .setSource("")
                                    .setSourceUuid(UUID_ALICE_STRING)
                                    .setTimestamp(System.currentTimeMillis())
                                    .setSourceDevice(1)
                                    .setType(Envelope.Type.CIPHERTEXT)
                                    .build();

    final Envelope secondMessage = Envelope.newBuilder()
                                     .setLegacyMessage(ByteString.copyFrom("second".getBytes()))
                                     .setSource("")
                                     .setSourceUuid(UUID_BOB_STRING)
                                     .setTimestamp(System.currentTimeMillis())
                                     .setSourceDevice(2)
                                     .setType(Envelope.Type.CIPHERTEXT)
                                     .build();

    List<OutgoingMessageEntity> pendingMessages     = new LinkedList<OutgoingMessageEntity>() {{
      add(new OutgoingMessageEntity(1, true, UUID.randomUUID(), firstMessage.getType().getNumber(), firstMessage.getRelay(),
                                    firstMessage.getTimestamp(), firstMessage.getSource(), UUID.fromString(firstMessage.getSourceUuid()),
                                    firstMessage.getSourceDevice(), firstMessage.getLegacyMessage().toByteArray(),
                                    firstMessage.getContent().toByteArray(), 0, UUID.fromString(firstMessage.getSourceUuid())));
      add(new OutgoingMessageEntity(2, false, UUID.randomUUID(), secondMessage.getType().getNumber(), secondMessage.getRelay(),
                                    secondMessage.getTimestamp(), secondMessage.getSource(), UUID.fromString(secondMessage.getSourceUuid()),
                                    secondMessage.getSourceDevice(), secondMessage.getLegacyMessage().toByteArray(),
                                    secondMessage.getContent().toByteArray(), 0, UUID.fromString(secondMessage.getSourceUuid())));
    }};

    OutgoingMessageEntityList   pendingMessagesList = new OutgoingMessageEntityList(pendingMessages, false);

    when(device.getId()).thenReturn(2L);
    when(device.getSignalingKey()).thenReturn(Base64.encodeBytes(new byte[52]));

    when(account.getAuthenticatedDevice()).thenReturn(Optional.of(device));
    when(account.getNumber()).thenReturn(UUID_ALICE_STRING);
    when(account.getUuid()).thenReturn(UUID_ALICE);

    final Device sender1device = mock(Device.class);

    Set<Device> sender1devices = new HashSet<Device>() {{
      add(sender1device);
    }};

    Account sender1 = mock(Account.class);
    when(sender1.getDevices()).thenReturn(sender1devices);

    when(accountsManager.get(UUID_ALICE)).thenReturn(Optional.of(sender1));
    when(accountsManager.get(UUID_BOB)).thenReturn(Optional.<Account>empty());

    when(storedMessages.getMessagesForDevice(account.getNumber(), account.getUuid(), device.getId()))
        .thenReturn(pendingMessagesList);

    final List<CompletableFuture<WebSocketResponseMessage>> futures = new LinkedList<>();
    final WebSocketClient                                   client  = mock(WebSocketClient.class);

    when(client.sendRequest(eq("PUT"), eq("/api/v1/message"), ArgumentMatchers.nullable(List.class), ArgumentMatchers.<Optional<byte[]>>any()))
        .thenAnswer(new Answer<CompletableFuture<WebSocketResponseMessage>>() {
          @Override
          public CompletableFuture<WebSocketResponseMessage> answer(InvocationOnMock invocationOnMock) throws Throwable {
            CompletableFuture<WebSocketResponseMessage> future = new CompletableFuture<>();
            futures.add(future);
            return future;
          }
        });

    WebsocketAddress websocketAddress = new WebsocketAddress(account.getNumber(), device.getId());
    WebSocketConnection connection = new WebSocketConnection(pushSender, receiptSender, storedMessages,
                                                             account, device, client, "onemoreid");

    connection.onDispatchSubscribed(websocketAddress.serialize());

    verify(client, times(2)).sendRequest(eq("PUT"), eq("/api/v1/message"), ArgumentMatchers.nullable(List.class), ArgumentMatchers.<Optional<byte[]>>any());

    assertEquals(futures.size(), 2);

    WebSocketResponseMessage response = mock(WebSocketResponseMessage.class);
    when(response.getStatus()).thenReturn(200);
    futures.get(1).complete(response);
    futures.get(0).completeExceptionally(new IOException());

    verify(receiptSender, times(1)).sendReceipt(eq(account), eq(UUID_BOB_STRING), eq(secondMessage.getTimestamp()));
    verifyNoMoreInteractions(websocketSender);
    verifyNoMoreInteractions(pushSender);

    connection.onDispatchUnsubscribed(websocketAddress.serialize());
    verify(client).close(anyInt(), anyString());
  }


  private OutgoingMessageEntity createMessage(long id, boolean cached, String sender, UUID senderUuid, long timestamp, boolean receipt, String content) {
    return new OutgoingMessageEntity(id, cached, UUID.randomUUID(), receipt ? Envelope.Type.RECEIPT_VALUE : Envelope.Type.CIPHERTEXT_VALUE,
                                     null, timestamp, sender, senderUuid, 1, content.getBytes(), null, 0, senderUuid);
  }

}
