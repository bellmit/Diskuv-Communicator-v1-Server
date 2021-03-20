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

import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.signal.storageservice.storage.protos.groups.Group;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration.CHECKSUM_SHARED_KEY_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;

/** @author Jonah Beckford */
public class GroupsDaoTest {

  @Rule public DDBServerRule ddbServer = new DDBServerRule();

  private GroupsDao dao;
  private TestableDynamoDbAsyncClientWrapper asyncClientWrapper;

  @Before
  public void setUp() {
    asyncClientWrapper = new TestableDynamoDbAsyncClientWrapper(ddbServer.client());
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(Set.of());

    dao = new GroupsDao(asyncClientWrapper.get(), "Groups", new byte[CHECKSUM_SHARED_KEY_SIZE]);

    // create the table
    dao.getTable().createTable().join();
  }

  @Test
  public void when_createGroup_then_returnsCreatedAsTrue() {
    // when
    Group group = Group.newBuilder().build();
    Boolean result = dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, group).join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isTrue();
  }

  @Test
  public void given_ddbFailsDuringPutItem_when_createGroup_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.PUT_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .putItem(any(PutItemRequest.class));

    // when / then
    Group group = Group.newBuilder().build();
    CompletionException completionException =
        assertThrows(
            CompletionException.class,
            () -> dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, group).join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }

  @Test
  public void when_createGroup_twice_then_secondCreationReturnsCreatedAsFalse() {
    // when
    Group group = Group.newBuilder().build();
    Boolean result =
        dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, group)
            .thenCompose(firstResult -> dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, group))
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isFalse();
  }

  @Test
  public void given_noGroups_when_getGroup_then_notPresent() {
    // given / when
    Optional<Group> group = dao.getGroup(GroupsTestObjects.GROUP_ID_ONE).join();

    // then
    assertThat(group).isNotPresent();
  }

  @Test
  public void given_ddbFailsDuringGetItem_when_getGroup_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.GET_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .getItem(any(GetItemRequest.class));

    // when / then
    CompletionException completionException =
        assertThrows(
            CompletionException.class, () -> dao.getGroup(GroupsTestObjects.GROUP_ID_ONE).join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }

  @Test
  public void given_groupOneIsCreated_when_getGroup_then_equivalentToGroupOne() {
    // given / when
    Optional<Group> group =
        dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, GroupsTestObjects.GROUP_ONE)
            .thenCompose(success -> dao.getGroup(GroupsTestObjects.GROUP_ID_ONE))
            .join();

    // then
    assertThat(group).isPresent();
    assertThat(group.get()).isEqualTo(GroupsTestObjects.GROUP_ONE);
  }

  @Test
  public void given_noGroups_when_updateGroup_then_throwsResourceNotFoundException() {
    // given / when / then
    CompletionException completionException =
        assertThrows(
            CompletionException.class,
            () ->
                dao.updateGroup(GroupsTestObjects.GROUP_ID_ONE, GroupsTestObjects.GROUP_ONE)
                    .join());
    assertThat(completionException).hasCauseInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  public void given_groupOneIsCreated_when_updateGroup_withModifications_then_resultIsTrue() {
    // given / when
    Boolean result =
        dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, GroupsTestObjects.GROUP_ONE)
            .thenCompose(
                createResult ->
                    dao.updateGroup(
                        GroupsTestObjects.GROUP_ID_ONE,
                        GroupsTestObjects.GROUP_ONE.toBuilder()
                            .setVersion(GroupsTestObjects.GROUP_ONE.getVersion() + 1)
                            .setTitle(ByteString.copyFromUtf8("changed title"))
                            .build()))
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isTrue();
  }

  @Test
  public void given_groupOneIsCreated_when_updateGroup_withUnmodifiedGroupOne_then_resultIsFalse() {
    // given / when
    Boolean result =
        dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, GroupsTestObjects.GROUP_ONE)
            .thenCompose(
                createResult ->
                    dao.updateGroup(GroupsTestObjects.GROUP_ID_ONE, GroupsTestObjects.GROUP_ONE))
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isFalse();
  }

  @Test
  public void given_ddbFailsDuringUpdateItem_when_updateGroup_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.UPDATE_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .updateItem(any(UpdateItemRequest.class));

    // when / then
    CompletionException completionException =
        assertThrows(
            CompletionException.class,
            () ->
                dao.createGroup(GroupsTestObjects.GROUP_ID_ONE, GroupsTestObjects.GROUP_ONE)
                    .thenCompose(
                        createResult ->
                            dao.updateGroup(
                                GroupsTestObjects.GROUP_ID_ONE,
                                GroupsTestObjects.GROUP_ONE.toBuilder()
                                    .setVersion(GroupsTestObjects.GROUP_ONE.getVersion() + 1)
                                    .setTitle(ByteString.copyFromUtf8("modified title"))
                                    .build()))
                    .join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }
}
