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
package org.whispersystems.textsecuregcm.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.StoredVerificationCode;
import org.whispersystems.textsecuregcm.experiment.Experiment;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import redis.clients.jedis.Jedis;

public class PendingAccountsManager {

  private final Logger logger = LoggerFactory.getLogger(PendingAccountsManager.class);

  private static final String CACHE_PREFIX = "pending_account2::";

  private final PendingAccounts           pendingAccounts;
  private final ReplicatedJedisPool       cacheClient;
  private final FaultTolerantRedisCluster cacheCluster;
  private final ObjectMapper              mapper;
  private final Experiment                redisClusterExperiment = new Experiment("RedisCluster", "PendingAccountsManager");

  public PendingAccountsManager(PendingAccounts pendingAccounts, ReplicatedJedisPool cacheClient, FaultTolerantRedisCluster cacheCluster)
  {
    this.pendingAccounts = pendingAccounts;
    this.cacheClient     = cacheClient;
    this.cacheCluster    = cacheCluster;
    this.mapper          = SystemMapper.getMapper();
  }

  public void store(UUID accountUuid, StoredVerificationCode code) {
    memcacheSet(accountUuid.toString(), code);
    pendingAccounts.insert(accountUuid, code.getCode(), code.getTimestamp(), code.getPushCode());
  }

  public void remove(UUID accountUuid) {
    memcacheDelete(accountUuid.toString());
    pendingAccounts.remove(accountUuid);
  }

  public Optional<StoredVerificationCode> getCodeForPendingAccount(UUID accountUuid) {
    Optional<StoredVerificationCode> code = memcacheGet(accountUuid.toString());

    if (!code.isPresent()) {
      code = pendingAccounts.getCodeForPendingAccount(accountUuid);
      code.ifPresent(storedVerificationCode -> memcacheSet(accountUuid.toString(), storedVerificationCode));
    }

    return code;
  }

  private void memcacheSet(String number, StoredVerificationCode code) {
    try (Jedis jedis = cacheClient.getWriteResource()) {
      final String key                  = CACHE_PREFIX + number;
      final String verificationCodeJson = mapper.writeValueAsString(code);

      jedis.set(key, verificationCodeJson);
      cacheCluster.useWriteCluster(connection -> connection.sync().set(key, verificationCodeJson));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private Optional<StoredVerificationCode> memcacheGet(String number) {
    try (Jedis jedis = cacheClient.getReadResource()) {
      final String key = CACHE_PREFIX + number;

      String json = jedis.get(key);
      redisClusterExperiment.compareSupplierResult(json, () -> cacheCluster.withReadCluster(connection -> connection.sync().get(key)));

      if (json == null) return Optional.empty();
      else              return Optional.of(mapper.readValue(json, StoredVerificationCode.class));
    } catch (IOException e) {
      logger.warn("Error deserializing value...", e);
      return Optional.empty();
    }
  }

  private void memcacheDelete(String number) {
    try (Jedis jedis = cacheClient.getWriteResource()) {
      final String key = CACHE_PREFIX + number;

      jedis.del(key);
      cacheCluster.useWriteCluster(connection -> connection.sync().del(key));
    }
  }
}
