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
import org.signal.zkgroup.groups.GroupIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.diskuv.communicatorservice.storage.DaoCommons.checkIsNotConditionalFailure;
import static com.diskuv.communicatorservice.storage.GroupItem.ATTRIBUTE_OPTIMISTIC_LOCK_VERSION;
import static com.diskuv.communicatorservice.storage.HouseItem.HOUSE_TABLE_SCHEMA;

/** Groups direct access object for CRUD operations on {@link GroupItem}s. */
public class HousesDao {
  private static final Logger  LOGGER                          = LoggerFactory.getLogger(HousesDao.class);
  private static final Integer INITIAL_OPTIMISTIC_LOCK_VERSION = null;

  private final DynamoDbAsyncClient asyncClient;
  private final DynamoDbAsyncTable<HouseItem> table;

  public HousesDao(DynamoDbAsyncClient asyncClient, String tableName) {
    Preconditions.checkArgument(asyncClient != null);
    Preconditions.checkArgument(tableName != null && tableName.length() > 0);

    this.asyncClient = asyncClient;
    DynamoDbEnhancedAsyncClient enhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(asyncClient).build();
    this.table = enhancedAsyncClient.table(tableName, HOUSE_TABLE_SCHEMA);
  }

  @VisibleForTesting
  public DynamoDbAsyncTable<HouseItem> getTable() {
    return table;
  }

  /**
   * Creates a house in DDB if it doesn't already exist.
   *
   * @return True if the house was created, or false if the house already existed.
   */
  public CompletableFuture<Boolean> createHouse(ByteString groupId, UUID supportContactId) {
    Preconditions.checkArgument(groupId != null);

    HouseItem item = toHouseItem(groupId, INITIAL_OPTIMISTIC_LOCK_VERSION, true, supportContactId);

    return checkIsNotConditionalFailure(table.putItem(item), "house");
  }

  /**
   * Gets a house from DDB.
   *
   * @return an optional house which is present if the house exists, or not present if the house
   *     does not exist
   */
  public CompletableFuture<Optional<HouseItem>> getHouse(ByteString groupId) {
    Preconditions.checkArgument(groupId != null);
    CompletableFuture<HouseItem> item = table.getItem(getKey(groupId));
    return item.thenApply(Optional::ofNullable);
  }

  public CompletableFuture<Void> startupProbe(SecureRandom secureRandom) {
    byte[] houseGroupId = new byte[GroupIdentifier.SIZE];
    secureRandom.nextBytes(houseGroupId);
    return getHouse(ByteString.copyFrom(houseGroupId))
        .exceptionally(
            throwable -> {
              throw new RuntimeException(
                  "Houses could not be probed. Check DynamoDB table: " + table.tableName(),
                  throwable);
            })
        .thenApply(
                unused -> {
                  LOGGER.info("Successful probe of {}", table.tableName());
                  return null;
                });
  }

  /** Updates a house. */
  public CompletableFuture<Void> updateHouse(HouseItem houseItem) {
    Preconditions.checkArgument(houseItem != null);
    return asyncClient
        .getItem(
            GetItemRequest.builder()
                .tableName(table.tableName())
                .key(
                    getKey(ByteString.copyFrom(houseItem.getHouseGroupId()))
                        .primaryKeyMap(HOUSE_TABLE_SCHEMA))
                .projectionExpression(ATTRIBUTE_OPTIMISTIC_LOCK_VERSION)
                .build())
        .thenApply(
            getItemResponse -> {
              if (!getItemResponse.hasItem()) {
                return Optional.<Integer>empty();
              }
              AttributeValue olv = getItemResponse.item().get(ATTRIBUTE_OPTIMISTIC_LOCK_VERSION);
              Preconditions.checkState(olv != null, "No optimistic lock present in record");
              Preconditions.checkState(olv.n() != null, "The optimistic lock was not a number");
              return Optional.of(Integer.parseInt(olv.n()));
            })
        .thenCompose(
            optimisticLockVersion -> {
              // if the house doesn't exist, return a ResourceNotFoundException failure
              if (optimisticLockVersion.isEmpty()) {
                return CompletableFuture.failedFuture(
                    ResourceNotFoundException.builder()
                        .message("The house could not be updated because it does not exist")
                        .build());
              }

              // otherwise try the update
              HouseItem updatedItem = houseItem.clone();
              updatedItem.setOptimisticLockVersion(optimisticLockVersion.get());
              return table.updateItem(updatedItem).thenApply(groupItem -> null);
            });
  }

  private static Key getKey(ByteString groupId) {
    Preconditions.checkArgument(groupId != null);
    return Key.builder().partitionValue(SdkBytes.fromByteArray(groupId.toByteArray())).build();
  }

  private HouseItem toHouseItem(
      ByteString groupId,
      @Nullable Integer optimisticLockVersion,
      boolean houseEnabled,
      UUID supportContactId) {
    Preconditions.checkArgument(groupId != null);
    HouseItem item = new HouseItem();
    item.setHouseGroupId(groupId.toByteArray());
    item.setOptimisticLockVersion(optimisticLockVersion);
    item.setHouseEnabled(houseEnabled);
    item.setSupportContactId(supportContactId.toString());
    return item;
  }
}
