// Copyright 2021 Diskuv, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.diskuv.communicatorservice.storage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChanges;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.diskuv.communicatorservice.storage.DaoCommons.checkIsNotConditionalFailure;
import static com.diskuv.communicatorservice.storage.GroupChangeItem.GROUP_LOG_TABLE_SCHEMA;

/**
 * Group log direct access object for CRUD operations on {@link GroupChangeItem}s.
 *
 * <p>Manages a cache of the immutable group logs if you supply a {@link GroupChangeCache} in the
 * constructor
 *
 * @author Jonah Beckford
 */
public class GroupLogDao {
  private static final Logger                              LOGGER = LoggerFactory.getLogger(GroupLogDao.class);
  private final        DynamoDbAsyncTable<GroupChangeItem> table;
  private final @Nullable GroupChangeCache cache;
  private final @Nullable Executor executorCacheCheck;

  public GroupLogDao(
      DynamoDbAsyncClient asyncClient, String tableName, Optional<GroupChangeCache> cache) {
    Preconditions.checkArgument(asyncClient != null);

    DynamoDbEnhancedAsyncClient enhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(asyncClient).build();
    this.table = enhancedAsyncClient.table(tableName, GROUP_LOG_TABLE_SCHEMA);
    this.cache = cache.orElse(null);
    this.executorCacheCheck =
        cache
            .map(
                c ->
                    Executors.newFixedThreadPool(
                        c.getSuggestedNumberOfCacheCheckingThreads(),
                        new ThreadFactoryBuilder()
                            .threadNamePrefix("grouplogcache-pool-%d")
                            .daemonThreads(true)
                            .build()))
            .orElse(null);
  }

  @VisibleForTesting
  public DynamoDbAsyncTable<GroupChangeItem> getTable() {
    return table;
  }

  /**
   * Append a group change within DDB.
   *
   * @return True if the group change was appended, or false if the group change already existed.
   */
  public CompletableFuture<Boolean> append(
      ByteString groupId, int version, GroupChange groupChange, Group group) {
    Preconditions.checkArgument(groupId != null);
    Preconditions.checkArgument(version >= 0);
    Preconditions.checkArgument(groupChange != null);
    Preconditions.checkArgument(group != null);

    GroupChangeItem item = toGroupChangeItem(groupId, group, groupChange, version);

    PutItemEnhancedRequest<GroupChangeItem> request =
        PutItemEnhancedRequest.builder(GroupChangeItem.class)
            .item(item)
            .conditionExpression(
                Expression.builder()
                    .expression(
                        String.format(
                            "attribute_not_exists(%s)", GroupChangeItem.ATTRIBUTE_GROUP_VERSION))
                    .build())
            .build();
    return checkIsNotConditionalFailure(table.putItem(request), "group change");
  }

  /** Gets a list of group changes from DDB. */
  public CompletableFuture<List<GroupChanges.GroupChangeState>> getRecordsFromVersion(
      ByteString groupId, int fromVersionInclusive, int toVersionExclusive) {
    Preconditions.checkArgument(groupId != null);
    Preconditions.checkArgument(fromVersionInclusive >= 0);
    Preconditions.checkArgument(toVersionExclusive >= 0);

    // if we don't have a cache, simply grab from the database
    if (executorCacheCheck == null) {
      return queryDatabaseForRemaining(
          groupId, fromVersionInclusive, toVersionExclusive, new CachedGroupChanges());
    }

    // otherwise, grab from the cache and also populate it as necessary
    return CompletableFuture.supplyAsync(
            () -> readAsMuchAsPossibleFromCache(groupId, fromVersionInclusive, toVersionExclusive),
            executorCacheCheck)
        .thenCompose(
            cachedGroupChanges ->
                queryDatabaseForRemaining(
                    groupId, fromVersionInclusive, toVersionExclusive, cachedGroupChanges));
  }

  public CompletableFuture<Void> startupProbe(SecureRandom secureRandom) {
    byte[] groupId = new byte[GroupIdentifier.SIZE];
    secureRandom.nextBytes(groupId);
    return getRecordsFromVersion(ByteString.copyFrom(groupId), 0, 1)
        .exceptionally(
            throwable -> {
              throw new RuntimeException(
                  "Group logs could not be probed. Check DynamoDB table: " + table.tableName(),
                  throwable);
            })
        .thenApply(
            unused -> {
              LOGGER.info("Successful probe of {}", table.tableName());
              return null;
            });
  }

  private CachedGroupChanges readAsMuchAsPossibleFromCache(
      ByteString groupId, int fromVersionInclusive, int toVersionExclusive) {
    // Read as many versions as we can grab from the cache (if we have a cache)
    CachedGroupChanges cachedGroupChanges = new CachedGroupChanges();
    if (cache == null) {
      return cachedGroupChanges;
    }
    for (int currentVersion = fromVersionInclusive;
        currentVersion < toVersionExclusive;
        ++currentVersion) {
      byte[] keyBytes = getKeyBytes(groupId, currentVersion);
      byte[] valueBytes = cache.getValueIfPresent(keyBytes);
      if (valueBytes == null) {
        return cachedGroupChanges;
      }
      final GroupChanges.GroupChangeState changeState;
      try {
        changeState = GroupChanges.GroupChangeState.parseFrom(valueBytes);
      } catch (InvalidProtocolBufferException e) {
        // this should never happen, except during an upgrade of the cache
        return cachedGroupChanges;
      }
      cachedGroupChanges.add(currentVersion, changeState);
    }
    return cachedGroupChanges;
  }

  private CompletableFuture<List<GroupChanges.GroupChangeState>> queryDatabaseForRemaining(
      ByteString groupId,
      int fromVersionInclusive,
      int toVersionExclusive,
      CachedGroupChanges cachedGroupChanges) {
    // use last change of `cachedGroupChanges`. we'll start one past that.
    final int startVersion;
    Integer lastCachedGroupVersion = cachedGroupChanges.getLastGroupVersion();
    if (lastCachedGroupVersion == null) {
      startVersion = fromVersionInclusive;
    } else {
      startVersion = lastCachedGroupVersion + 1;
    }

    // use something thread safe for the changes

    // query the database for anything remaining
    if (startVersion >= toVersionExclusive) {
      return CompletableFuture.completedFuture(cachedGroupChanges.getGroupChanges());
    }
    final ConcurrentLinkedDeque<GroupChanges.GroupChangeState> threadSafeResult =
        new ConcurrentLinkedDeque<>(cachedGroupChanges.getGroupChanges());
    PagePublisher<GroupChangeItem> queryPublisher =
        table.query(
            QueryConditional.sortBetween(
                getKey(groupId, startVersion), getKey(groupId, toVersionExclusive - 1)));
    return queryPublisher
        // scatter the pages (in reality, DDB probably goes through one page at a time)
        .subscribe(
            groupChangeItemPage -> {
              List<GroupChanges.GroupChangeState> collectedGroupChanges = new ArrayList<>();
              for (GroupChangeItem item : groupChangeItemPage.items()) {
                try {
                  GroupChanges.GroupChangeState groupChangeState =
                      GroupChanges.GroupChangeState.newBuilder()
                          .setGroupChange(GroupChange.parseFrom(item.getGroupChangeBytes()))
                          .setGroupState(Group.parseFrom(item.getGroupBytes()))
                          .build();
                  collectedGroupChanges.add(groupChangeState);

                  // populate the cache as well, if present
                  if (cache != null) {
                    byte[] keyBytes = getKeyBytes(groupId, item.getGroupVersion());
                    cache.putValue(keyBytes, groupChangeState.toByteArray());
                  }
                } catch (InvalidProtocolBufferException e) {
                  throw new IllegalStateException(e);
                }
              }
              threadSafeResult.addAll(collectedGroupChanges);
            })
        // and gather
        .thenApply(unused -> new ArrayList<>(threadSafeResult));
  }

  private static Key getKey(ByteString groupId, int version) {
    Preconditions.checkArgument(groupId != null);
    return Key.builder()
        .partitionValue(SdkBytes.fromByteArray(groupId.toByteArray()))
        .sortValue(version)
        .build();
  }

  @VisibleForTesting
  static byte[] getKeyBytes(ByteString groupId, int version) {
    ByteBuffer buffer = ByteBuffer.allocate(groupId.size() + 4);
    buffer.put(groupId.asReadOnlyByteBuffer());
    buffer.putInt(version);
    return buffer.array();
  }

  private GroupChangeItem toGroupChangeItem(
      ByteString groupId, Group group, GroupChange groupChange, int groupVersion) {
    Preconditions.checkArgument(groupId != null);
    Preconditions.checkArgument(group != null);
    Preconditions.checkArgument(groupChange != null);
    GroupChangeItem item = new GroupChangeItem();
    item.setGroupId(groupId.toByteArray());
    item.setGroupBytes(group.toByteArray());
    item.setGroupChangeBytes(groupChange.toByteArray());
    item.setGroupVersion(groupVersion);
    return item;
  }
}
