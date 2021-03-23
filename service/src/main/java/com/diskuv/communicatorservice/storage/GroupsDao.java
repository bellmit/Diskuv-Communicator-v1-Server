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
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.diskuv.communicatorservice.storage.DaoCommons.checkIsNotConditionalFailure;
import static com.diskuv.communicatorservice.storage.GroupItem.*;
import static com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration.CHECKSUM_SHARED_KEY_SIZE;

/** Groups direct access object for CRUD operations on {@link GroupItem}s. */
public class GroupsDao {
  private static final Logger  LOGGER                          = LoggerFactory.getLogger(GroupsDao.class);
  private static final Integer INITIAL_OPTIMISTIC_LOCK_VERSION = null;

  private final DynamoDbAsyncClient asyncClient;
  private final DynamoDbAsyncTable<GroupItem> table;
  private final HashFunction checksumFunction;

  public GroupsDao(DynamoDbAsyncClient asyncClient, String tableName, byte[] checksumSharedKey) {
    Preconditions.checkArgument(asyncClient != null);
    Preconditions.checkArgument(tableName != null && tableName.length() > 0);
    Preconditions.checkArgument(
        checksumSharedKey != null && checksumSharedKey.length == CHECKSUM_SHARED_KEY_SIZE);

    this.asyncClient = asyncClient;
    DynamoDbEnhancedAsyncClient enhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(asyncClient).build();
    this.table = enhancedAsyncClient.table(tableName, GROUPS_TABLE_SCHEMA);

    ByteBuffer buffer = ByteBuffer.wrap(checksumSharedKey);
    long k0 = buffer.getLong();
    long k1 = buffer.getLong();
    this.checksumFunction = Hashing.sipHash24(k0, k1);
  }

  @VisibleForTesting
  public DynamoDbAsyncTable<GroupItem> getTable() {
    return table;
  }

  /**
   * Creates a group in DDB if it doesn't already exist.
   *
   * @return True if the group was created, or false if the group already existed.
   */
  public CompletableFuture<Boolean> createGroup(ByteString groupId, Group group) {
    Preconditions.checkArgument(groupId != null);
    Preconditions.checkArgument(group != null);

    byte[] groupBytes = group.toByteArray();
    GroupItem item =
        toGroupItem(
            groupId,
            INITIAL_OPTIMISTIC_LOCK_VERSION,
            group.getVersion(),
            groupBytes,
            getChecksum(groupBytes));

    return checkIsNotConditionalFailure(table.putItem(item), "group");
  }

  /**
   * Gets a group from DDB.
   *
   * @return an optional group which is present if the group exists, or not present if the group
   *     does not exist
   */
  public CompletableFuture<Optional<Group>> getGroup(ByteString groupId) {
    Preconditions.checkArgument(groupId != null);
    CompletableFuture<GroupItem> item = table.getItem(getKey(groupId));
    return item.thenApply(
        groupItem -> groupItem != null ? Optional.of(toGroup(groupItem)) : Optional.empty());
  }

  public CompletableFuture<Void> startupProbe(SecureRandom secureRandom) {
    byte[] groupId = new byte[GroupIdentifier.SIZE];
    secureRandom.nextBytes(groupId);
    return getGroup(ByteString.copyFrom(groupId))
        .exceptionally(
            throwable -> {
              throw new RuntimeException(
                  "Groups could not be probed. Check DynamoDB table: " + table.tableName(),
                  throwable);
            })
        .thenApply(
                unused -> {
                  LOGGER.info("Successful probe of {}", table.tableName());
                  return null;
                });
  }

  /**
   * Updates a group.
   *
   * <p>If the group has no changes from what is in DDB, no update occurs. If the group has the same
   * version or an older version than the latest in DDB, no update occurs.
   *
   * @return true if the group was updated to a new value, or false otherwise
   */
  public CompletableFuture<Boolean> updateGroup(ByteString groupId, Group group) {
    Preconditions.checkArgument(groupId != null);
    Preconditions.checkArgument(group != null);
    return asyncClient
        .getItem(
            GetItemRequest.builder()
                .tableName(table.tableName())
                .key(getKey(groupId).primaryKeyMap(GROUPS_TABLE_SCHEMA))
                .projectionExpression(
                    Joiner.on(',')
                        .join(
                            ATTRIBUTE_OPTIMISTIC_LOCK_VERSION,
                            ATTRIBUTE_GROUP_BYTES_CHECKSUM,
                            ATTRIBUTE_GROUP_VERSION))
                .build())
        .thenApply(
            getItemResponse -> {
              if (!getItemResponse.hasItem()) {
                return Optional.<OptimisticLockAndGroupVersionsAndChecksum>empty();
              }
              AttributeValue olv = getItemResponse.item().get(ATTRIBUTE_OPTIMISTIC_LOCK_VERSION);
              AttributeValue gv = getItemResponse.item().get(ATTRIBUTE_GROUP_VERSION);
              AttributeValue checksum = getItemResponse.item().get(ATTRIBUTE_GROUP_BYTES_CHECKSUM);
              Preconditions.checkState(olv != null, "No optimistic lock present in record");
              Preconditions.checkState(olv.n() != null, "The optimistic lock was not a number");
              Preconditions.checkState(gv != null, "No group version present in record");
              Preconditions.checkState(gv.n() != null, "The group version was not a number");
              Preconditions.checkState(checksum != null, "No checksum present in record");
              Preconditions.checkState(checksum.b() != null, "The checksum was not bytes");
              return Optional.of(
                  new OptimisticLockAndGroupVersionsAndChecksum(
                      Integer.parseInt(olv.n()),
                      Integer.parseInt(gv.n()),
                      checksum.b().asByteArray()));
            })
        .thenCompose(
            existingVersionsAndChecksum -> {
              // if the group doesn't exist, return a ResourceNotFoundException failure
              if (existingVersionsAndChecksum.isEmpty()) {
                return CompletableFuture.failedFuture(
                    ResourceNotFoundException.builder()
                        .message("The group could not be updated because it does not exist")
                        .build());
              }
              // if the existing group is not one version older than what we want to update, return
              // false
              if (existingVersionsAndChecksum.get().getGroupVersion() != group.getVersion() - 1) {
                return CompletableFuture.completedFuture(false);
              }

              // if the existing group has the same groupBytesChecksum as what to want to update
              // towards, return false
              byte[] groupBytes = group.toByteArray();
              byte[] groupBytesChecksum = getChecksum(groupBytes);
              if (Arrays.equals(
                  groupBytesChecksum, existingVersionsAndChecksum.get().getChecksum())) {
                return CompletableFuture.completedFuture(false);
              }

              // otherwise try the update
              return table
                  .updateItem(
                      toGroupItem(
                          groupId,
                          existingVersionsAndChecksum.get().getOptimisticLockVersion(),
                          group.getVersion(),
                          groupBytes,
                          groupBytesChecksum))
                  .thenApply(groupItem -> true);
            });
  }

  private static Key getKey(ByteString groupId) {
    Preconditions.checkArgument(groupId != null);
    return Key.builder().partitionValue(SdkBytes.fromByteArray(groupId.toByteArray())).build();
  }

  private static Group toGroup(GroupItem groupItem) {
    Preconditions.checkArgument(groupItem != null);
    try {
      return Group.parseFrom(groupItem.getGroupBytes());
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private GroupItem toGroupItem(
      ByteString groupId,
      @Nullable Integer optimisticLockVersion,
      int groupVersion,
      byte[] groupBytes,
      byte[] groupBytesChecksum) {
    Preconditions.checkArgument(groupId != null);
    Preconditions.checkArgument(groupBytes != null);
    Preconditions.checkArgument(groupBytesChecksum != null);
    GroupItem item = new GroupItem();
    item.setGroupId(groupId.toByteArray());
    item.setOptimisticLockVersion(optimisticLockVersion);
    item.setGroupVersion(groupVersion);
    item.setGroupBytes(groupBytes);
    item.setGroupBytesChecksum(groupBytesChecksum);
    return item;
  }

  private byte[] getChecksum(byte[] groupBytes) {
    Preconditions.checkArgument(groupBytes != null);
    return checksumFunction.hashBytes(groupBytes).asBytes();
  }
}
