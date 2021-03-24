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
import static com.diskuv.communicatorservice.storage.SanctuaryItem.HOUSE_TABLE_SCHEMA;

/** Groups direct access object for CRUD operations on {@link GroupItem}s. */
public class SanctuariesDao {
  private static final Logger  LOGGER                          = LoggerFactory.getLogger(SanctuariesDao.class);
  private static final Integer INITIAL_OPTIMISTIC_LOCK_VERSION = null;

  private final DynamoDbAsyncClient asyncClient;
  private final DynamoDbAsyncTable<SanctuaryItem> table;

  public SanctuariesDao(DynamoDbAsyncClient asyncClient, String tableName) {
    Preconditions.checkArgument(asyncClient != null);
    Preconditions.checkArgument(tableName != null && tableName.length() > 0);

    this.asyncClient = asyncClient;
    DynamoDbEnhancedAsyncClient enhancedAsyncClient =
        DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(asyncClient).build();
    this.table = enhancedAsyncClient.table(tableName, HOUSE_TABLE_SCHEMA);
  }

  @VisibleForTesting
  public DynamoDbAsyncTable<SanctuaryItem> getTable() {
    return table;
  }

  /**
   * Creates a sanctuary in DDB if it doesn't already exist.
   *
   * @return True if the sanctuary was created, or false if the sanctuary already existed.
   */
  public CompletableFuture<Boolean> createSanctuary(ByteString groupId, UUID supportContactId) {
    Preconditions.checkArgument(groupId != null);

    SanctuaryItem item = toSanctuaryItem(groupId, INITIAL_OPTIMISTIC_LOCK_VERSION, true, supportContactId);

    return checkIsNotConditionalFailure(table.putItem(item), "sanctuary");
  }

  /**
   * Gets a sanctuary from DDB.
   *
   * @return an optional sanctuary which is present if the sanctuary exists, or not present if the sanctuary
   *     does not exist
   */
  public CompletableFuture<Optional<SanctuaryItem>> getSanctuary(ByteString groupId) {
    Preconditions.checkArgument(groupId != null);
    CompletableFuture<SanctuaryItem> item = table.getItem(getKey(groupId));
    return item.thenApply(Optional::ofNullable);
  }

  public CompletableFuture<Void> startupProbe(SecureRandom secureRandom) {
    byte[] sanctuaryGroupId = new byte[GroupIdentifier.SIZE];
    secureRandom.nextBytes(sanctuaryGroupId);
    return getSanctuary(ByteString.copyFrom(sanctuaryGroupId))
        .exceptionally(
            throwable -> {
              throw new RuntimeException(
                  "Sanctuaries could not be probed. Check DynamoDB table: " + table.tableName(),
                  throwable);
            })
        .thenApply(
                unused -> {
                  LOGGER.info("Successful probe of {}", table.tableName());
                  return null;
                });
  }

  /** Updates a sanctuary. */
  public CompletableFuture<Void> updateSanctuary(SanctuaryItem sanctuaryItem) {
    Preconditions.checkArgument(sanctuaryItem != null);
    return asyncClient
        .getItem(
            GetItemRequest.builder()
                .tableName(table.tableName())
                .key(
                    getKey(ByteString.copyFrom(sanctuaryItem.getSanctuaryGroupId()))
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
              // if the sanctuary doesn't exist, return a ResourceNotFoundException failure
              if (optimisticLockVersion.isEmpty()) {
                return CompletableFuture.failedFuture(
                    ResourceNotFoundException.builder()
                        .message("The sanctuary could not be updated because it does not exist")
                        .build());
              }

              // otherwise try the update
              SanctuaryItem updatedItem = sanctuaryItem.clone();
              updatedItem.setOptimisticLockVersion(optimisticLockVersion.get());
              return table.updateItem(updatedItem).thenApply(groupItem -> null);
            });
  }

  private static Key getKey(ByteString groupId) {
    Preconditions.checkArgument(groupId != null);
    return Key.builder().partitionValue(SdkBytes.fromByteArray(groupId.toByteArray())).build();
  }

  private SanctuaryItem toSanctuaryItem(
      ByteString groupId,
      @Nullable Integer optimisticLockVersion,
      boolean sanctuaryEnabled,
      UUID supportContactId) {
    Preconditions.checkArgument(groupId != null);
    SanctuaryItem item = new SanctuaryItem();
    item.setSanctuaryGroupId(groupId.toByteArray());
    item.setOptimisticLockVersion(optimisticLockVersion);
    item.setSanctuaryEnabled(sanctuaryEnabled);
    item.setSupportContactId(supportContactId.toString());
    return item;
  }
}
