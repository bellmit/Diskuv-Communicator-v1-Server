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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChanges;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.diskuv.communicatorservice.storage.CacheEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

/** @author Jonah Beckford */
public class GroupLogDaoTest {

  private static final int VERSION_1 = 1;
  private static final int VERSION_2 = 2;
  private static final int VERSION_3 = 3;
  private static final int VERSION_4 = 4;
  @Rule public DDBServerRule ddbServer = new DDBServerRule();

  private GroupLogDao dao;
  private TestableDynamoDbAsyncClientWrapper asyncClientWrapper;

  @Before
  public void setUp() {
    asyncClientWrapper = new TestableDynamoDbAsyncClientWrapper(ddbServer.client());
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(Set.of());

    dao = new GroupLogDao(asyncClientWrapper.get(), "GroupLog", Optional.empty());

    // create the table
    dao.getTable().createTable().join();
  }

  @Test
  public void when_append_then_returnsCreatedAsTrue() {
    // when
    Group group = Group.newBuilder().build();
    GroupChange groupChange = GroupChange.newBuilder().build();
    Boolean result =
        dao.append(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, groupChange, group).join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isTrue();
  }

  @Test
  public void given_ddbFailsDuringPutItem_when_append_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.PUT_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .putItem(any(PutItemRequest.class));

    // when / then
    Group group = Group.newBuilder().build();
    GroupChange groupChange = GroupChange.newBuilder().build();
    CompletionException completionException =
        assertThrows(
            CompletionException.class,
            () -> dao.append(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, groupChange, group).join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }

  @Test
  public void when_append_twice_with_same_version_then_secondCreationReturnsCreatedAsFalse() {
    // when
    Group group = Group.newBuilder().build();
    GroupChange groupChange = GroupChange.newBuilder().build();
    Boolean result =
        dao.append(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, groupChange, group)
            .thenCompose(
                firstResult ->
                    dao.append(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, groupChange, group))
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isFalse();
  }

  @Test
  public void given_noGroupLog_when_getRecordsFromVersion_then_empty() {
    // given / when
    List<GroupChanges.GroupChangeState> group =
        dao.getRecordsFromVersion(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, VERSION_3).join();

    // then
    assertThat(group).isEmpty();
  }

  @Test
  public void given_ddbFailsDuringQuery_when_getRecordsFromVersion_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.QUERY));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .query(any(QueryRequest.class));

    // when / then
    CompletionException completionException =
        assertThrows(
            CompletionException.class,
            () ->
                dao.getRecordsFromVersion(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, VERSION_3)
                    .join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }

  @Test
  public void
      given_TwoGroupChangesAreCreated_when_getRecordsFromVersion_then_equivalentListOfTwo() {
    // given / when
    List<GroupChanges.GroupChangeState> changes =
        dao.append(
                GroupsTestObjects.GROUP_ID_ONE,
                VERSION_1,
                GroupsTestObjects.GROUP_CHANGE_ONE,
                GroupsTestObjects.GROUP_ONE)
            .thenCompose(
                success ->
                    dao.append(
                        GroupsTestObjects.GROUP_ID_ONE,
                        VERSION_2,
                        GroupsTestObjects.GROUP_CHANGE_TWO,
                        GroupsTestObjects.GROUP_ONE))
            .thenCompose(
                success ->
                    dao.getRecordsFromVersion(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, VERSION_3))
            .join();

    // then
    assertThat(changes)
        .containsExactly(
            GroupChanges.GroupChangeState.newBuilder()
                .setGroupState(GroupsTestObjects.GROUP_ONE)
                .setGroupChange(GroupsTestObjects.GROUP_CHANGE_ONE)
                .build(),
            GroupChanges.GroupChangeState.newBuilder()
                .setGroupState(GroupsTestObjects.GROUP_ONE)
                .setGroupChange(GroupsTestObjects.GROUP_CHANGE_TWO)
                .build());
  }

  @Test
  public void
      given_cacheAndThreeGroupChangesAreCreated_when_getRecordsFromVersion_forFirstTwoChanges_when_getRecordsFromVersion_forAllThreeChanges_then_cacheIsReusedForFirstTwoChanges() {
    // given: cache
    Cache<ByteString, ByteString> testCache = CacheBuilder.newBuilder().build();
    List<CacheEvent> cacheEvents = mock(List.class);
    GroupChangeCache realCache =
        new GroupChangeCache() {
          @Override
          public int getSuggestedNumberOfCacheCheckingThreads() {
            // make sure no weird threading effects
            return 1;
          }

          @Nullable
          @Override
          public byte[] getValueIfPresent(@Nonnull byte[] keyBytes) {
            ByteString bytes = testCache.getIfPresent(ByteString.copyFrom(keyBytes));
            cacheEvents.add(bytes == null ? cacheMiss(keyBytes) : cacheHit(keyBytes));
            if (bytes == null) {
              return null;
            }
            return bytes.toByteArray();
          }

          @Override
          public void putValue(@Nonnull byte[] keyBytes, @Nonnull byte[] valueBytes) {
            testCache.put(ByteString.copyFrom(keyBytes), ByteString.copyFrom(valueBytes));
            cacheEvents.add(cachePut(keyBytes));
          }
        };
    dao = new GroupLogDao(asyncClientWrapper.get(), "GroupLog", Optional.of(realCache));

    // given / when
    dao.append(
            GroupsTestObjects.GROUP_ID_ONE,
            VERSION_1,
            GroupsTestObjects.GROUP_CHANGE_ONE,
            GroupsTestObjects.GROUP_ONE)
        .thenCompose(
            success ->
                dao.append(
                    GroupsTestObjects.GROUP_ID_ONE,
                    VERSION_2,
                    GroupsTestObjects.GROUP_CHANGE_TWO,
                    GroupsTestObjects.GROUP_ONE))
        .thenCompose(
            success ->
                dao.append(
                    GroupsTestObjects.GROUP_ID_ONE,
                    VERSION_3,
                    GroupsTestObjects.GROUP_CHANGE_THREE,
                    GroupsTestObjects.GROUP_ONE))
        // A: get first two changes
        .thenCompose(
            success ->
                dao.getRecordsFromVersion(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, VERSION_3))
        // B: get all changes
        .thenCompose(
            success ->
                dao.getRecordsFromVersion(GroupsTestObjects.GROUP_ID_ONE, VERSION_1, VERSION_4))
        .join();

    // then ...
    InOrder inOrder = inOrder(cacheEvents);

    // ... cache starts to be used in point "A" for the first dao.getRecordsFromVersion with [1,3)

    // ... cache miss for GROUP_CHANGE_ONE
    inOrder
        .verify(cacheEvents)
        .add(eq(cacheMiss(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_1))));

    // ... so will immediately start reading from the database at GROUP_CHANGE_ONE (and populating
    // the cache each record)
    inOrder
        .verify(cacheEvents)
        .add(eq(cachePut(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_1))));
    inOrder
        .verify(cacheEvents)
        .add(eq(cachePut(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_2))));

    // ... cache starts to be used in point "B" for the second dao.getRecordsFromVersion with [1,4)

    // ... cache hit for GROUP_CHANGE_ONE
    inOrder
        .verify(cacheEvents)
        .add(eq(cacheHit(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_1))));

    // ... cache hit for GROUP_CHANGE_TWO
    inOrder
        .verify(cacheEvents)
        .add(eq(cacheHit(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_2))));

    // ... cache miss for GROUP_CHANGE_THREE
    inOrder
        .verify(cacheEvents)
        .add(eq(cacheMiss(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_3))));

    // ... so will immediately start reading from the database at GROUP_CHANGE_THREE (and populating
    // the cache each record)
    inOrder
        .verify(cacheEvents)
        .add(eq(cachePut(GroupLogDao.getKeyBytes(GroupsTestObjects.GROUP_ID_ONE, VERSION_3))));

    // ... and no more uses of the cache
    inOrder.verifyNoMoreInteractions();
  }
}
