package com.diskuv.communicatorservice.storage;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;

class CacheEvent {
  private final @Nonnull
  CacheEventType eventType;
  private final @Nonnull
  byte[] keyBytes;
  private final @Nonnull
  CacheGetResponse getResponse;

  public CacheEvent(@Nonnull CacheEventType eventType,
                    @Nonnull byte[] keyBytes,
                    @Nonnull CacheGetResponse getResponse) {
    this.eventType = eventType;
    this.keyBytes = keyBytes;
    this.getResponse = getResponse;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CacheEvent that = (CacheEvent) o;
    return eventType == that.eventType && Arrays.equals(keyBytes,
                                                        that.keyBytes) && getResponse == that.getResponse;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(eventType, getResponse);
    result = 31 * result + Arrays.hashCode(keyBytes);
    return result;
  }

  @Override
  public String toString() {
    return "CacheEvent{" +
            "eventType=" + eventType +
            ", keyBytes=" + Arrays.toString(keyBytes) +
            ", getResponse=" + getResponse +
            '}';
  }

  public static CacheEvent cacheHit(@Nonnull byte[] keyBytes) {
    return new CacheEvent(CacheEventType.GET, keyBytes, CacheGetResponse.CACHE_HIT);
  }

  public static CacheEvent cacheMiss(@Nonnull byte[] keyBytes) {
    return new CacheEvent(CacheEventType.GET, keyBytes, CacheGetResponse.CACHE_MISS);
  }

  public static CacheEvent cachePut(@Nonnull byte[] keyBytes) {
    return new CacheEvent(CacheEventType.PUT, keyBytes, CacheGetResponse.NOT_A_GET);
  }
}
