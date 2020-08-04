package org.whispersystems.textsecuregcm.redis;

import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.lettuce.core.RedisException;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.cluster.pubsub.api.sync.RedisClusterPubSubCommands;
import org.junit.Before;
import org.junit.Test;
import org.whispersystems.textsecuregcm.configuration.CircuitBreakerConfiguration;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FaultTolerantRedisClusterTest {

    private RedisAdvancedClusterCommands<String, String> clusterCommands;
    private RedisClusterPubSubCommands<String, String>   pubSubCommands;

    private FaultTolerantRedisCluster faultTolerantCluster;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        final RedisClusterClient                                   clusterClient     = mock(RedisClusterClient.class);
        final StatefulRedisClusterConnection<String, String>       clusterConnection = mock(StatefulRedisClusterConnection.class);
        final StatefulRedisClusterPubSubConnection<String, String> pubSubConnection  = mock(StatefulRedisClusterPubSubConnection.class);

        clusterCommands = mock(RedisAdvancedClusterCommands.class);
        pubSubCommands  = mock(RedisClusterPubSubCommands.class);

        when(clusterClient.connect()).thenReturn(clusterConnection);
        when(clusterClient.connectPubSub()).thenReturn(pubSubConnection);
        when(clusterConnection.sync()).thenReturn(clusterCommands);
        when(pubSubConnection.sync()).thenReturn(pubSubCommands);

        final CircuitBreakerConfiguration breakerConfiguration = new CircuitBreakerConfiguration();
        breakerConfiguration.setFailureRateThreshold(100);
        breakerConfiguration.setRingBufferSizeInClosedState(1);
        breakerConfiguration.setWaitDurationInOpenStateInSeconds(Integer.MAX_VALUE);

        faultTolerantCluster = new FaultTolerantRedisCluster("test", clusterClient, Duration.ofSeconds(2), breakerConfiguration);
    }

    @Test
    public void testReadBreaker() {
        when(clusterCommands.get(anyString()))
                .thenReturn("value")
                .thenThrow(new RedisException("Badness has ensued."));

        assertEquals("value", faultTolerantCluster.withReadCluster(connection -> connection.sync().get("key")));

        assertThrows(RedisException.class,
                () -> faultTolerantCluster.withReadCluster(connection -> connection.sync().get("OH NO")));

        assertThrows(CircuitBreakerOpenException.class,
                () -> faultTolerantCluster.withReadCluster(connection -> connection.sync().get("OH NO")));
    }

    @Test
    public void testReadsContinueWhileWriteBreakerOpen() {
        when(clusterCommands.set(anyString(), anyString())).thenThrow(new RedisException("Badness has ensued."));

        assertThrows(RedisException.class,
                () -> faultTolerantCluster.useWriteCluster(connection -> connection.sync().set("OH", "NO")));

        assertThrows(CircuitBreakerOpenException.class,
                () -> faultTolerantCluster.useWriteCluster(connection -> connection.sync().set("OH", "NO")));

        when(clusterCommands.get("key")).thenReturn("value");

        assertEquals("value", faultTolerantCluster.withReadCluster(connection -> connection.sync().get("key")));
    }

    @Test
    public void testWriteBreaker() {
        when(clusterCommands.get(anyString()))
                .thenReturn("value")
                .thenThrow(new RedisException("Badness has ensued."));

        assertEquals("value", faultTolerantCluster.withWriteCluster(connection -> connection.sync().get("key")));

        assertThrows(RedisException.class,
                () -> faultTolerantCluster.withWriteCluster(connection -> connection.sync().get("OH NO")));

        assertThrows(CircuitBreakerOpenException.class,
                () -> faultTolerantCluster.withWriteCluster(connection -> connection.sync().get("OH NO")));
    }

    @Test
    public void testPubSubBreaker() {
        when(pubSubCommands.publish(anyString(), anyString()))
                .thenReturn(1L)
                .thenThrow(new RedisException("Badness has ensued."));

        assertEquals(1L, (long)faultTolerantCluster.withPubSubConnection(connection -> connection.sync().publish("channel", "message")));

        assertThrows(RedisException.class,
                () -> faultTolerantCluster.withPubSubConnection(connection -> connection.sync().publish("channel", "OH NO")));

        assertThrows(CircuitBreakerOpenException.class,
                () -> faultTolerantCluster.withPubSubConnection(connection -> connection.sync().publish("channel", "OH NO")));
    }
}
