/**
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
package org.whispersystems.textsecuregcm.limits;


import java.util.concurrent.atomic.AtomicReference;
import org.whispersystems.textsecuregcm.configuration.RateLimitsConfiguration;
import org.whispersystems.textsecuregcm.configuration.RateLimitsConfiguration.CardinalityRateLimitConfiguration;
import org.whispersystems.textsecuregcm.configuration.RateLimitsConfiguration.RateLimitConfiguration;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.storage.DynamicConfigurationManager;

public class RateLimiters {

  private final RateLimiter smsDestinationLimiter;
  private final RateLimiter voiceDestinationLimiter;
  private final RateLimiter voiceDestinationDailyLimiter;
  private final RateLimiter smsVoiceIpLimiter;
  private final RateLimiter smsVoicePrefixLimiter;
  private final RateLimiter autoBlockLimiter;
  private final RateLimiter verifyLimiter;
  private final RateLimiter pinLimiter;

  private final RateLimiter attachmentLimiter;
  private final RateLimiter preKeysLimiter;
  private final RateLimiter messagesLimiter;

  private final RateLimiter allocateDeviceLimiter;
  private final RateLimiter verifyDeviceLimiter;

  private final RateLimiter turnLimiter;

  private final RateLimiter profileLimiter;
  private final RateLimiter stickerPackLimiter;
  private final RateLimiter usernameLookupLimiter;
  private final RateLimiter usernameSetLimiter;
  private final RateLimiter connectWebSocketLimiter;
  private final RateLimiter sanctuaryLookupLimiter;

  private final AtomicReference<CardinalityRateLimiter> unsealedSenderLimiter;
  private final AtomicReference<RateLimiter> unsealedIpLimiter;

  private final FaultTolerantRedisCluster   cacheCluster;
  private final DynamicConfigurationManager dynamicConfig;

  public RateLimiters(RateLimitsConfiguration config, DynamicConfigurationManager dynamicConfig, FaultTolerantRedisCluster cacheCluster) {
    this.cacheCluster  = cacheCluster;
    this.dynamicConfig = dynamicConfig;

    this.smsDestinationLimiter = new RateLimiter(cacheCluster, "smsDestination",
                                                 config.getSmsDestination().getBucketSize(),
                                                 config.getSmsDestination().getLeakRatePerMinute());

    this.voiceDestinationLimiter = new RateLimiter(cacheCluster, "voxDestination",
                                                   config.getVoiceDestination().getBucketSize(),
                                                   config.getVoiceDestination().getLeakRatePerMinute());

    this.voiceDestinationDailyLimiter = new RateLimiter(cacheCluster, "voxDestinationDaily",
                                                        config.getVoiceDestinationDaily().getBucketSize(),
                                                        config.getVoiceDestinationDaily().getLeakRatePerMinute());

    this.smsVoiceIpLimiter = new RateLimiter(cacheCluster, "smsVoiceIp",
                                             config.getSmsVoiceIp().getBucketSize(),
                                             config.getSmsVoiceIp().getLeakRatePerMinute());

    this.smsVoicePrefixLimiter = new RateLimiter(cacheCluster, "smsVoicePrefix",
                                                 config.getSmsVoicePrefix().getBucketSize(),
                                                 config.getSmsVoicePrefix().getLeakRatePerMinute());

    this.autoBlockLimiter = new RateLimiter(cacheCluster, "autoBlock",
                                            config.getAutoBlock().getBucketSize(),
                                            config.getAutoBlock().getLeakRatePerMinute());

    this.verifyLimiter = new LockingRateLimiter(cacheCluster, "verify",
                                                config.getVerifyNumber().getBucketSize(),
                                                config.getVerifyNumber().getLeakRatePerMinute());

    this.pinLimiter = new LockingRateLimiter(cacheCluster, "pin",
                                             config.getVerifyPin().getBucketSize(),
                                             config.getVerifyPin().getLeakRatePerMinute());

    this.attachmentLimiter = new RateLimiter(cacheCluster, "attachmentCreate",
                                             config.getAttachments().getBucketSize(),
                                             config.getAttachments().getLeakRatePerMinute());

    this.preKeysLimiter = new RateLimiter(cacheCluster, "prekeys",
                                          config.getPreKeys().getBucketSize(),
                                          config.getPreKeys().getLeakRatePerMinute());

    this.messagesLimiter = new RateLimiter(cacheCluster, "messages",
                                           config.getMessages().getBucketSize(),
                                           config.getMessages().getLeakRatePerMinute());

    this.allocateDeviceLimiter = new RateLimiter(cacheCluster, "allocateDevice",
                                                 config.getAllocateDevice().getBucketSize(),
                                                 config.getAllocateDevice().getLeakRatePerMinute());

    this.verifyDeviceLimiter = new RateLimiter(cacheCluster, "verifyDevice",
                                               config.getVerifyDevice().getBucketSize(),
                                               config.getVerifyDevice().getLeakRatePerMinute());

    this.turnLimiter = new RateLimiter(cacheCluster, "turnAllocate",
                                       config.getTurnAllocations().getBucketSize(),
                                       config.getTurnAllocations().getLeakRatePerMinute());

    this.profileLimiter = new RateLimiter(cacheCluster, "profile",
                                          config.getProfile().getBucketSize(),
                                          config.getProfile().getLeakRatePerMinute());

    this.stickerPackLimiter = new RateLimiter(cacheCluster, "stickerPack",
                                              config.getStickerPack().getBucketSize(),
                                              config.getStickerPack().getLeakRatePerMinute());

    this.usernameLookupLimiter = new RateLimiter(cacheCluster, "usernameLookup",
                                                 config.getUsernameLookup().getBucketSize(),
                                                 config.getUsernameLookup().getLeakRatePerMinute());

    this.usernameSetLimiter = new RateLimiter(cacheCluster, "usernameSet",
                                              config.getUsernameSet().getBucketSize(),
                                              config.getUsernameSet().getLeakRatePerMinute());

    this.connectWebSocketLimiter = new RateLimiter(cacheCluster, "connectWebSocket",
                                              config.getConnectWebSocket().getBucketSize(),
                                              config.getConnectWebSocket().getLeakRatePerMinute());

    this.sanctuaryLookupLimiter = new RateLimiter(cacheCluster, "sanctuaryLookup",
                                                   config.getSanctuaryLookup().getBucketSize(),
                                                   config.getSanctuaryLookup().getLeakRatePerMinute());

    this.unsealedSenderLimiter = new AtomicReference<>(createUnsealedSenderLimiter(cacheCluster, dynamicConfig.getConfiguration().getLimits().getUnsealedSenderNumber()));
    this.unsealedIpLimiter     = new AtomicReference<>(createUnsealedIpLimiter(cacheCluster, dynamicConfig.getConfiguration().getLimits().getUnsealedSenderIp()));
  }

  public CardinalityRateLimiter getUnsealedSenderLimiter() {
    CardinalityRateLimitConfiguration currentConfiguration = dynamicConfig.getConfiguration().getLimits().getUnsealedSenderNumber();

    return this.unsealedSenderLimiter.updateAndGet(rateLimiter -> {
      if (rateLimiter.hasConfiguration(currentConfiguration)) {
        return rateLimiter;
      } else {
        return createUnsealedSenderLimiter(cacheCluster, currentConfiguration);
      }
    });
  }

  public RateLimiter getUnsealedIpLimiter() {
    RateLimitConfiguration currentConfiguration = dynamicConfig.getConfiguration().getLimits().getUnsealedSenderIp();

    return this.unsealedIpLimiter.updateAndGet(rateLimiter -> {
      if (rateLimiter.hasConfiguration(currentConfiguration)) {
        return rateLimiter;
      } else {
        return createUnsealedIpLimiter(cacheCluster, currentConfiguration);
      }
    });
  }

  public RateLimiter getAllocateDeviceLimiter() {
    return allocateDeviceLimiter;
  }

  public RateLimiter getVerifyDeviceLimiter() {
    return verifyDeviceLimiter;
  }

  public RateLimiter getMessagesLimiter() {
    return messagesLimiter;
  }

  public RateLimiter getPreKeysLimiter() {
    return preKeysLimiter;
  }

  public RateLimiter getAttachmentLimiter() {
    return this.attachmentLimiter;
  }

  public RateLimiter getSmsDestinationLimiter() {
    return smsDestinationLimiter;
  }

  public RateLimiter getSmsVoiceIpLimiter() {
    return smsVoiceIpLimiter;
  }

  public RateLimiter getSmsVoicePrefixLimiter() {
    return smsVoicePrefixLimiter;
  }

  public RateLimiter getAutoBlockLimiter() {
    return autoBlockLimiter;
  }

  public RateLimiter getVoiceDestinationLimiter() {
    return voiceDestinationLimiter;
  }

  public RateLimiter getVoiceDestinationDailyLimiter() {
    return voiceDestinationDailyLimiter;
  }

  public RateLimiter getVerifyLimiter() {
    return verifyLimiter;
  }

  public RateLimiter getPinLimiter() {
    return pinLimiter;
  }

  public RateLimiter getTurnLimiter() {
    return turnLimiter;
  }

  public RateLimiter getProfileLimiter() {
    return profileLimiter;
  }

  public RateLimiter getStickerPackLimiter() {
    return stickerPackLimiter;
  }

  public RateLimiter getUsernameLookupLimiter() {
    return usernameLookupLimiter;
  }

  public RateLimiter getUsernameSetLimiter() {
    return usernameSetLimiter;
  }

  public RateLimiter getConnectWebSocketLimiter() {
    return connectWebSocketLimiter;
  }

  public RateLimiter getSanctuaryLookupLimiter() {
    return sanctuaryLookupLimiter;
  }

  private CardinalityRateLimiter createUnsealedSenderLimiter(FaultTolerantRedisCluster cacheCluster, CardinalityRateLimitConfiguration configuration) {
    return new CardinalityRateLimiter(cacheCluster, "unsealedSender", configuration.getTtl(), configuration.getTtlJitter(), configuration.getMaxCardinality());
  }

  private RateLimiter createUnsealedIpLimiter(FaultTolerantRedisCluster cacheCluster, RateLimitConfiguration configuration)
  {
    return createLimiter(cacheCluster, configuration, "unsealedIp");
  }

  private RateLimiter createLimiter(FaultTolerantRedisCluster cacheCluster, RateLimitConfiguration configuration, String name) {
    return new RateLimiter(cacheCluster, name,
                           configuration.getBucketSize(),
                           configuration.getLeakRatePerMinute());
  }
}
