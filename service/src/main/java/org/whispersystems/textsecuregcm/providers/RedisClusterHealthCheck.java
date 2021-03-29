package org.whispersystems.textsecuregcm.providers;

import com.codahale.metrics.health.HealthCheck;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;

public class RedisClusterHealthCheck extends HealthCheck {

    private final FaultTolerantRedisCluster redisCluster;

    public RedisClusterHealthCheck(final FaultTolerantRedisCluster redisCluster) {
        this.redisCluster = redisCluster;
    }

    @Override
    protected Result check() {
        redisCluster.withCluster(connection -> connection.sync().upstream().commands().ping());
        return Result.healthy();
    }
}
