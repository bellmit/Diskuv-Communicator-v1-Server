package org.whispersystems.textsecuregcm.storage;

import com.diskuv.communicatorservice.storage.GroupChangeCache;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;

import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

public class RedisBackedGroupChangeCache implements GroupChangeCache {
  private static final byte[] GROUP_CHANGE_KEY_PREFIX =
      "groupchange:".getBytes(StandardCharsets.UTF_8);
  private final FaultTolerantRedisCluster cacheCluster;
  private final int numberOfCacheCheckingThreads;

  public RedisBackedGroupChangeCache(
      FaultTolerantRedisCluster cacheCluster, int numberOfCacheCheckingThreads) {
    Preconditions.checkArgument(numberOfCacheCheckingThreads > 0);
    this.cacheCluster = cacheCluster;
    this.numberOfCacheCheckingThreads = numberOfCacheCheckingThreads;
  }

  @Override
  public int getSuggestedNumberOfCacheCheckingThreads() {
    return numberOfCacheCheckingThreads;
  }

  @Nullable
  @Override
  public byte[] getValueIfPresent(@Nonnull byte[] keyBytes) {
    return cacheCluster.withBinaryCluster(connection -> {
      return connection.sync().get(Bytes.concat(GROUP_CHANGE_KEY_PREFIX, keyBytes));
    });
  }

  @Override
  public void putValue(@Nonnull byte[] keyBytes, @Nonnull byte[] valueBytes) {
    cacheCluster.useBinaryCluster(connection -> {
      connection.sync().set(Bytes.concat(GROUP_CHANGE_KEY_PREFIX, keyBytes), valueBytes);
    });
  }
}
