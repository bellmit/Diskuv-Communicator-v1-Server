package org.whispersystems.textsecuregcm.websocket;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.opentable.db.postgres.embedded.LiquibasePreparer;
import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;
import org.apache.commons.lang3.RandomStringUtils;
import org.jdbi.v3.core.Jdbi;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.whispersystems.textsecuregcm.configuration.CircuitBreakerConfiguration;
import org.whispersystems.textsecuregcm.entities.MessageProtos;
import org.whispersystems.textsecuregcm.experiment.ExperimentEnrollmentManager;
import org.whispersystems.textsecuregcm.metrics.PushLatencyManager;
import org.whispersystems.textsecuregcm.push.ReceiptSender;
import org.whispersystems.textsecuregcm.redis.AbstractRedisClusterTest;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.FaultTolerantDatabase;
import org.whispersystems.textsecuregcm.storage.Messages;
import org.whispersystems.textsecuregcm.storage.MessagesCache;
import org.whispersystems.textsecuregcm.storage.MessagesDynamoDb;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.tests.util.MessagesDynamoDbRule;
import org.whispersystems.websocket.WebSocketClient;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebSocketConnectionIntegrationTest extends AbstractRedisClusterTest {

    @Rule
    public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(LiquibasePreparer.forClasspathLocation("messagedb.xml"));

    @Rule
    public MessagesDynamoDbRule messagesDynamoDbRule = new MessagesDynamoDbRule();

    private ExecutorService executorService;
    private Messages messages;
    private MessagesDynamoDb messagesDynamoDb;
    private MessagesCache messagesCache;
    private Account account;
    private Device device;
    private WebSocketClient webSocketClient;
    private WebSocketConnection webSocketConnection;
    private ExperimentEnrollmentManager experimentEnrollmentManager;

    private long serialTimestamp = System.currentTimeMillis();

    @Before
    public void setupAccountsDao() {
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        executorService = Executors.newSingleThreadExecutor();
        messages = new Messages(new FaultTolerantDatabase("messages-test", Jdbi.create(db.getTestDatabase()), new CircuitBreakerConfiguration()));
        messagesCache = new MessagesCache(getRedisCluster(), getRedisCluster(), executorService);
        messagesDynamoDb = new MessagesDynamoDb(messagesDynamoDbRule.getDynamoDB(), MessagesDynamoDbRule.TABLE_NAME, Duration.ofDays(7));
        account = mock(Account.class);
        device = mock(Device.class);
        webSocketClient = mock(WebSocketClient.class);
        experimentEnrollmentManager = mock(ExperimentEnrollmentManager.class);

        UUID uuid = org.whispersystems.textsecuregcm.util.DiskuvUuidUtil.uuidForOutdoorEmailAddress(new java.util.Random().nextLong() + "@example.com");
        when(account.getNumber()).thenReturn(uuid.toString());
        when(account.getUuid()).thenReturn(uuid);
        when(device.getId()).thenReturn(1L);
        when(experimentEnrollmentManager.isEnrolled(any(UUID.class), anyString())).thenReturn(Boolean.FALSE);

        webSocketConnection = new WebSocketConnection(
                mock(ReceiptSender.class),
                new MessagesManager(messages, messagesDynamoDb, messagesCache, mock(PushLatencyManager.class), experimentEnrollmentManager),
                account,
                device,
                webSocketClient);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.SECONDS);

        super.tearDown();
    }

    @Test(timeout = 15_000)
    public void testProcessStoredMessages() throws InterruptedException {
        final int persistedMessageCount = 207;
        final int cachedMessageCount    = 173;

        final List<MessageProtos.Envelope> expectedMessages = new ArrayList<>(persistedMessageCount + cachedMessageCount);

        {
            final List<MessageProtos.Envelope> persistedMessages = new ArrayList<>(persistedMessageCount);

            for (int i = 0; i < persistedMessageCount; i++) {
                final MessageProtos.Envelope envelope = generateRandomMessage(UUID.randomUUID());

                persistedMessages.add(envelope);
                expectedMessages.add(envelope.toBuilder().clearServerGuid().build());
            }

            messages.store(persistedMessages, account.getUuid().toString(), device.getId());
        }

        for (int i = 0; i < cachedMessageCount; i++) {
            final UUID messageGuid = UUID.randomUUID();
            final MessageProtos.Envelope envelope = generateRandomMessage(messageGuid);

            messagesCache.insert(messageGuid, account.getUuid(), device.getId(), envelope);
            expectedMessages.add(envelope.toBuilder().clearServerGuid().build());
        }

        final WebSocketResponseMessage successResponse = mock(WebSocketResponseMessage.class);
        final AtomicBoolean queueCleared = new AtomicBoolean(false);

        when(successResponse.getStatus()).thenReturn(200);
        when(webSocketClient.sendRequest(eq("PUT"), eq("/api/v1/message"), anyList(), any())).thenReturn(CompletableFuture.completedFuture(successResponse));

        when(webSocketClient.sendRequest(eq("PUT"), eq("/api/v1/queue/empty"), anyList(), any())).thenAnswer((Answer<CompletableFuture<WebSocketResponseMessage>>)invocation -> {
            synchronized (queueCleared) {
                queueCleared.set(true);
                queueCleared.notifyAll();
            }

            return CompletableFuture.completedFuture(successResponse);
        });

        webSocketConnection.processStoredMessages();

        synchronized (queueCleared) {
            while (!queueCleared.get()) {
                queueCleared.wait();
            }
        }

        final ArgumentCaptor<Optional<byte[]>> messageBodyCaptor = ArgumentCaptor.forClass(Optional.class);

        verify(webSocketClient, times(persistedMessageCount + cachedMessageCount)).sendRequest(eq("PUT"), eq("/api/v1/message"), anyList(), messageBodyCaptor.capture());
        verify(webSocketClient).sendRequest(eq("PUT"), eq("/api/v1/queue/empty"), anyList(), eq(Optional.empty()));

        final List<MessageProtos.Envelope> sentMessages = new ArrayList<>();

        for (final Optional<byte[]> maybeMessageBody : messageBodyCaptor.getAllValues()) {
            maybeMessageBody.ifPresent(messageBytes -> {
                try {
                    sentMessages.add(MessageProtos.Envelope.parseFrom(messageBytes));
                } catch (final InvalidProtocolBufferException e) {
                    fail("Could not parse sent message");
                }
            });
        }

        assertEquals(expectedMessages, sentMessages);
    }

    @Test(timeout = 15_000)
    public void testProcessStoredMessagesClientClosed() {
        final int persistedMessageCount = 207;
        final int cachedMessageCount = 173;

        {
            final List<MessageProtos.Envelope> persistedMessages = new ArrayList<>(persistedMessageCount);

            for (int i = 0; i < persistedMessageCount; i++) {
                persistedMessages.add(generateRandomMessage(UUID.randomUUID()));
            }

            messages.store(persistedMessages, account.getNumber(), device.getId());
        }

        for (int i = 0; i < cachedMessageCount; i++) {
            final UUID messageGuid = UUID.randomUUID();
            messagesCache.insert(messageGuid, account.getUuid(), device.getId(), generateRandomMessage(messageGuid));
        }

        when(webSocketClient.sendRequest(eq("PUT"), eq("/api/v1/message"), anyList(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("Connection closed")));

        webSocketConnection.processStoredMessages();

        verify(webSocketClient, atMost(persistedMessageCount + cachedMessageCount)).sendRequest(eq("PUT"), eq("/api/v1/message"), anyList(), any());
        verify(webSocketClient, never()).sendRequest(eq("PUT"), eq("/api/v1/queue/empty"), anyList(), eq(Optional.empty()));
    }

    private MessageProtos.Envelope generateRandomMessage(final UUID messageGuid) {
        final long timestamp = serialTimestamp++;

        return MessageProtos.Envelope.newBuilder()
                .setTimestamp(timestamp)
                .setServerTimestamp(timestamp)
                .setContent(ByteString.copyFromUtf8(RandomStringUtils.randomAlphanumeric(256)))
                .setType(MessageProtos.Envelope.Type.CIPHERTEXT)
                .setServerGuid(messageGuid.toString())
                .build();
    }
}
