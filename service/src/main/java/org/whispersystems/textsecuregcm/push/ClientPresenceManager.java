package org.whispersystems.textsecuregcm.push;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.lifecycle.Managed;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.event.ClusterTopologyChangedEvent;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.cluster.pubsub.RedisClusterPubSubAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.redis.ClusterLuaScript;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.RedisClusterUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * The client presence manager keeps track of which clients are actively connected and "present" to receive messages.
 * Only one client per account/device may be present at a time; if a second client for the same account/device declares
 * its presence, the previous client is displaced.
 */
public class ClientPresenceManager extends RedisClusterPubSubAdapter<String, String> implements Managed {

    private final String managerId             = UUID.randomUUID().toString();
    private final String connectedClientSetKey = getConnectedClientSetKey(managerId);

    private final FaultTolerantRedisCluster presenceCluster;
    private final ClusterLuaScript          clearPresenceScript;

    private final ScheduledExecutorService scheduledExecutorService;
    private       ScheduledFuture<?>       pruneMissingPeersFuture;

    private final Map<String, DisplacedPresenceListener> displacementListenersByPresenceKey = new ConcurrentHashMap<>();

    private final Timer checkPresenceTimer;
    private final Timer setPresenceTimer;
    private final Timer clearPresenceTimer;
    private final Timer prunePeersTimer;
    private final Meter pruneClientMeter;
    private final Meter remoteDisplacementMeter;

    private static final int PRUNE_PEERS_INTERVAL_SECONDS = (int)Duration.ofMinutes(3).toSeconds();

    static final String MANAGER_SET_KEY = "presence::managers";

    private static final Logger log = LoggerFactory.getLogger(ClientPresenceManager.class);

    public ClientPresenceManager(final FaultTolerantRedisCluster presenceCluster, final ScheduledExecutorService scheduledExecutorService) throws IOException {
        this.presenceCluster          = presenceCluster;
        this.scheduledExecutorService = scheduledExecutorService;
        this.clearPresenceScript      = ClusterLuaScript.fromResource(presenceCluster, "lua/clear_presence.lua", ScriptOutputType.INTEGER);

        final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
        metricRegistry.gauge(name(getClass(), "localClientCount"), () -> displacementListenersByPresenceKey::size);

        this.checkPresenceTimer      = metricRegistry.timer(name(getClass(), "checkPresence"));
        this.setPresenceTimer        = metricRegistry.timer(name(getClass(), "setPresence"));
        this.clearPresenceTimer      = metricRegistry.timer(name(getClass(), "clearPresence"));
        this.prunePeersTimer         = metricRegistry.timer(name(getClass(), "prunePeers"));
        this.pruneClientMeter        = metricRegistry.meter(name(getClass(), "pruneClient"));
        this.remoteDisplacementMeter = metricRegistry.meter(name(getClass(), "remoteDisplacement"));
    }

    @Override
    public void start() {
        RedisClusterUtil.assertKeyspaceNotificationsConfigured(presenceCluster, "K$");

        presenceCluster.usePubSubConnection(connection -> {
            connection.addListener(this);
            connection.getResources().eventBus().get()
                                                .filter(event -> event instanceof ClusterTopologyChangedEvent)
                                                .handle((event, sink) -> {
                                                    resubscribeAll();
                                                    sink.next(event);
                                                });

            final String presenceChannel = getManagerPresenceChannel(managerId);
            final int    slot            = SlotHash.getSlot(presenceChannel);

            connection.sync().nodes(node -> node.is(RedisClusterNode.NodeFlag.MASTER) && node.hasSlot(slot)).commands().subscribe(presenceChannel);
        });

        presenceCluster.useWriteCluster(connection -> connection.sync().sadd(MANAGER_SET_KEY, managerId));

        pruneMissingPeersFuture = scheduledExecutorService.scheduleAtFixedRate(this::pruneMissingPeers, new Random().nextInt(PRUNE_PEERS_INTERVAL_SECONDS), PRUNE_PEERS_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        presenceCluster.usePubSubConnection(connection -> connection.removeListener(this));

        if (pruneMissingPeersFuture != null) {
            pruneMissingPeersFuture.cancel(false);
        }

        for (final String presenceKey : displacementListenersByPresenceKey.keySet()) {
            clearPresence(presenceKey);
        }

        presenceCluster.useWriteCluster(connection -> {
            connection.sync().srem(MANAGER_SET_KEY, managerId);
            connection.sync().del(getConnectedClientSetKey(managerId));
        });

        presenceCluster.usePubSubConnection(connection -> connection.sync().masters().commands().unsubscribe(getManagerPresenceChannel(managerId)));
    }

    public void setPresent(final UUID accountUuid, final long deviceId, final DisplacedPresenceListener displacementListener) {
        try (final Timer.Context ignored = setPresenceTimer.time()) {
            final String presenceKey = getPresenceKey(accountUuid, deviceId);

            displacePresence(presenceKey);

            displacementListenersByPresenceKey.put(presenceKey, displacementListener);

            presenceCluster.useWriteCluster(connection -> {
                final RedisAdvancedClusterCommands<String, String> commands = connection.sync();

                commands.set(presenceKey, managerId);
                commands.sadd(connectedClientSetKey, presenceKey);
            });

            subscribeForRemotePresenceChanges(presenceKey);
        }
    }

    private void displacePresence(final String presenceKey) {
        final DisplacedPresenceListener displacementListener = displacementListenersByPresenceKey.get(presenceKey);

        if (displacementListener != null) {
            displacementListener.handleDisplacement();
        }

        clearPresence(presenceKey);
    }

    public boolean isPresent(final UUID accountUuid, final long deviceId) {
        try (final Timer.Context ignored = checkPresenceTimer.time()) {
            return presenceCluster.withReadCluster(connection -> connection.sync().exists(getPresenceKey(accountUuid, deviceId))) == 1;
        }
    }

    public boolean clearPresence(final UUID accountUuid, final long deviceId) {
        return clearPresence(getPresenceKey(accountUuid, deviceId));
    }

    private boolean clearPresence(final String presenceKey) {
        try (final Timer.Context ignored = clearPresenceTimer.time()) {
            displacementListenersByPresenceKey.remove(presenceKey);
            unsubscribeFromRemotePresenceChanges(presenceKey);

            final boolean removed = clearPresenceScript.execute(List.of(presenceKey), List.of(managerId)) != null;
            presenceCluster.useWriteCluster(connection -> connection.sync().srem(connectedClientSetKey, presenceKey));

            return removed;
        }
    }

    private void subscribeForRemotePresenceChanges(final String presenceKey) {
        final int slot = SlotHash.getSlot(presenceKey);

        presenceCluster.usePubSubConnection(connection -> connection.sync().nodes(node -> node.is(RedisClusterNode.NodeFlag.MASTER) && node.hasSlot(slot))
                                                                           .commands()
                                                                           .subscribe(getKeyspaceNotificationChannel(presenceKey)));
    }

    private void resubscribeAll() {
        for (final String presenceKey : displacementListenersByPresenceKey.keySet()) {
            subscribeForRemotePresenceChanges(presenceKey);
        }
    }

    private void unsubscribeFromRemotePresenceChanges(final String presenceKey) {
        presenceCluster.usePubSubConnection(connection -> connection.async().masters().commands().unsubscribe(getKeyspaceNotificationChannel(presenceKey)));
    }

    void pruneMissingPeers() {
        try (final Timer.Context ignored = prunePeersTimer.time()) {
            final Set<String> peerIds = presenceCluster.withReadCluster(connection -> connection.sync().smembers(MANAGER_SET_KEY));
            peerIds.remove(managerId);

            for (final String peerId : peerIds) {
                final boolean peerMissing = presenceCluster.withWriteCluster(connection -> connection.sync().publish(getManagerPresenceChannel(peerId), "ping") == 0);

                if (peerMissing) {
                    log.debug("Presence manager {} did not respond to ping", peerId);

                    final String connectedClientsKey = getConnectedClientSetKey(peerId);

                    presenceCluster.useWriteCluster(connection -> {
                        final RedisAdvancedClusterCommands<String, String> commands = connection.sync();

                        String presenceKey;

                        while ((presenceKey = commands.spop(connectedClientsKey)) != null) {
                            clearPresenceScript.execute(List.of(presenceKey), List.of(peerId));
                            pruneClientMeter.mark();
                        }

                        commands.del(connectedClientsKey);
                        commands.srem(MANAGER_SET_KEY, peerId);
                    });
                }
            }
        }
    }

    @Override
    public void message(final RedisClusterNode node, final String channel, final String message) {
        if ("set".equals(message) && channel.startsWith("__keyspace@0__:presence::{")) {
            // Another process has overwritten this presence key, which means the client has connected to another host.
            // At this point, we're on a Lettuce IO thread and need to dispatch to a separate thread before making
            // synchronous Lettuce calls to avoid deadlocking.
            scheduledExecutorService.execute(() -> {
                displacePresence(channel.substring("__keyspace@0__:".length()));
                remoteDisplacementMeter.mark();
            });
        }
    }

    @VisibleForTesting
    static String getPresenceKey(final UUID accountUuid, final long deviceId) {
        return "presence::{" + accountUuid.toString() + "::" + deviceId + "}";
    }

    private static String getKeyspaceNotificationChannel(final String presenceKey) {
        return "__keyspace@0__:" + presenceKey;
    }

    @VisibleForTesting
    static String getConnectedClientSetKey(final String managerId) {
        return "presence::clients::" + managerId;
    }

    @VisibleForTesting
    static String getManagerPresenceChannel(final String managerId) {
        return "presence::manager::" + managerId;
    }
}
