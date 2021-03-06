/*
 * Copyright (C) 2018 Open WhisperSystems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import org.whispersystems.textsecuregcm.redis.ClusterLuaScript;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import redis.clients.jedis.exceptions.JedisException;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class AccountDatabaseCrawlerCache {
  private static final Logger logger = LoggerFactory.getLogger(AccountDatabaseCrawlerCache.class);
  private static final String ACTIVE_WORKER_KEY = "account_database_crawler_cache_active_worker";
  private static final String LAST_UUID_KEY     = "account_database_crawler_cache_last_uuid";
  private static final String ACCELERATE_KEY    = "account_database_crawler_cache_accelerate";

  private static final long LAST_NUMBER_TTL_MS  = 86400_000L;

  private final FaultTolerantRedisCluster cacheCluster;
  private final ClusterLuaScript          unlockClusterScript;

  public AccountDatabaseCrawlerCache(FaultTolerantRedisCluster cacheCluster) throws IOException {
    this.cacheCluster        = cacheCluster;
    String resource = "lua/account_database_crawler/unlock.lua";
      try {
        logger.info("Getting " + resource + " script from redis cluster");
        this.unlockClusterScript = ClusterLuaScript.fromResource(cacheCluster, resource, ScriptOutputType.INTEGER);
        logger.info("Got " + resource + " script from redis cluster");
      } catch (IOException | JedisException e) {
        logger.error("Could not get the "+resource+" script from redis cluster. You might need to increase the redis timeout in org.whispersystems.textsecuregcm.WhisperServerConfiguration.getCacheConfiguration()", e);
        throw e;
      }
  }

  public void setAccelerated(final boolean accelerated) {
    if (accelerated) {
      cacheCluster.useCluster(connection -> connection.sync().set(ACCELERATE_KEY, "1"));
    } else {
      cacheCluster.useCluster(connection -> connection.sync().del(ACCELERATE_KEY));
    }
  }

  public boolean isAccelerated() {
    return "1".equals(cacheCluster.withCluster(connection -> connection.sync().get(ACCELERATE_KEY)));
  }

  public boolean claimActiveWork(String workerId, long ttlMs) {
    return "OK".equals(cacheCluster.withCluster(connection -> connection.sync().set(ACTIVE_WORKER_KEY, workerId, SetArgs.Builder.nx().px(ttlMs))));
  }

  public void releaseActiveWork(String workerId) {
    unlockClusterScript.execute(List.of(ACTIVE_WORKER_KEY), List.of(workerId));
  }

  public Optional<UUID> getLastUuid() {
    final String lastUuidString = cacheCluster.withCluster(connection -> connection.sync().get(LAST_UUID_KEY));

    if (lastUuidString == null) return Optional.empty();
    else                        return Optional.of(UUID.fromString(lastUuidString));
  }

  public void setLastUuid(Optional<UUID> lastUuid) {
    if (lastUuid.isPresent()) {
      cacheCluster.useCluster(connection -> connection.sync().psetex(LAST_UUID_KEY, LAST_NUMBER_TTL_MS, lastUuid.get().toString()));
    } else {
      cacheCluster.useCluster(connection -> connection.sync().del(LAST_UUID_KEY));
    }
  }

}
