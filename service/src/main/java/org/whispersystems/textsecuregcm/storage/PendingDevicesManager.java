/*
 * Copyright (C) 2014 Open WhisperSystems
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
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import redis.clients.jedis.Jedis;

public class PendingDevicesManager {

  private final Logger logger = LoggerFactory.getLogger(PendingDevicesManager.class);

  private static final String CACHE_PREFIX = "pending_devices2::";

  private final PendingDevices      pendingDevices;
  private final ReplicatedJedisPool cacheClient;
  private final ObjectMapper        mapper;

  public PendingDevicesManager(PendingDevices pendingDevices, ReplicatedJedisPool cacheClient) {
    this.pendingDevices = pendingDevices;
    this.cacheClient    = cacheClient;
    this.mapper         = SystemMapper.getMapper();
  }

  public void store(UUID accountUuid, StoredVerificationCode code) {
    memcacheSet(accountUuid.toString(), code);
    pendingDevices.insert(accountUuid, code.getCode(), code.getTimestamp());
  }

  public void remove(UUID accountUuid) {
    memcacheDelete(accountUuid.toString());
    pendingDevices.remove(accountUuid);
  }

  public Optional<StoredVerificationCode> getCodeForPendingDevice(UUID accountUuid) {
    Optional<StoredVerificationCode> code = memcacheGet(accountUuid.toString());

    if (!code.isPresent()) {
      code = pendingDevices.getCodeForPendingDevice(accountUuid);
      code.ifPresent(storedVerificationCode -> memcacheSet(accountUuid.toString(), storedVerificationCode));
    }

    return code;
  }

  private void memcacheSet(String number, StoredVerificationCode code) {
    try (Jedis jedis = cacheClient.getWriteResource()) {
      jedis.set(CACHE_PREFIX + number, mapper.writeValueAsString(code));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private Optional<StoredVerificationCode> memcacheGet(String number) {
    try (Jedis jedis = cacheClient.getReadResource()) {
      String json = jedis.get(CACHE_PREFIX + number);

      if (json == null) return Optional.empty();
      else              return Optional.of(mapper.readValue(json, StoredVerificationCode.class));
    } catch (IOException e) {
      logger.warn("Could not parse pending device stored verification json");
      return Optional.empty();
    }
  }

  private void memcacheDelete(String number) {
    try (Jedis jedis = cacheClient.getWriteResource()) {
      jedis.del(CACHE_PREFIX + number);
    }
  }

}
