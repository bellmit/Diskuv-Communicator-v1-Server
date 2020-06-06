package org.whispersystems.textsecuregcm.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

public class ProfilesManager {

  private final Logger logger = LoggerFactory.getLogger(ProfilesManager.class);

  private static final String CACHE_PREFIX = "profiles::";

  private final Profiles                  profiles;
  private final ReplicatedJedisPool       cacheClient;
  private final FaultTolerantRedisCluster cacheCluster;
  private final ObjectMapper              mapper;

  public ProfilesManager(Profiles profiles, ReplicatedJedisPool cacheClient, FaultTolerantRedisCluster cacheCluster) {
    this.profiles     = profiles;
    this.cacheClient  = cacheClient;
    this.cacheCluster = cacheCluster;
    this.mapper       = SystemMapper.getMapper();
  }

  public void set(UUID uuid, VersionedProfile versionedProfile) {
    memcacheSet(uuid, versionedProfile);
    profiles.set(uuid, versionedProfile);
  }

  public void deleteAll(UUID uuid) {
    memcacheDelete(uuid);
    profiles.deleteAll(uuid);
  }

  public Optional<VersionedProfile> get(UUID uuid, String version) {
    Optional<VersionedProfile> profile = memcacheGet(uuid, version);

    if (!profile.isPresent()) {
      profile = profiles.get(uuid, version);
      profile.ifPresent(versionedProfile -> memcacheSet(uuid, versionedProfile));
    }

    return profile;
  }

  private void memcacheSet(UUID uuid, VersionedProfile profile) {
    try (Jedis jedis = cacheClient.getWriteResource()) {
      final String key         = CACHE_PREFIX + uuid.toString();
      final String profileJson = mapper.writeValueAsString(profile);

      jedis.hset(key, profile.getVersion(), profileJson);
      cacheCluster.useWriteCluster(connection -> connection.async().hset(key, profile.getVersion(), profileJson));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private Optional<VersionedProfile> memcacheGet(UUID uuid, String version) {
    try (Jedis jedis = cacheClient.getReadResource()) {
      String json = jedis.hget(CACHE_PREFIX + uuid.toString(), version);

      if (json == null) return Optional.empty();
      else              return Optional.of(mapper.readValue(json, VersionedProfile.class));
    } catch (IOException e) {
      logger.warn("Error deserializing value...", e);
      return Optional.empty();
    } catch (JedisException e) {
      logger.warn("Redis exception", e);
      return Optional.empty();
    }
  }

  private void memcacheDelete(UUID uuid) {
    try (Jedis jedis = cacheClient.getWriteResource()) {
      final String key = CACHE_PREFIX + uuid.toString();

      jedis.del(key);
      cacheCluster.useWriteCluster(connection -> connection.async().del(key));
    }
  }
}
