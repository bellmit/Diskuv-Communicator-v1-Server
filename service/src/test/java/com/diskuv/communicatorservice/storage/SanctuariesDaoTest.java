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
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;

/** @author Jonah Beckford */
public class SanctuariesDaoTest {

  private static final UUID SUPPORT_CONTACT_ID = UUID.randomUUID();
  private static final UUID SUPPORT_CONTACT_ID2 = UUID.randomUUID();
  @Rule public DDBServerRule ddbServer = new DDBServerRule();

  private SanctuariesDao dao;
  private TestableDynamoDbAsyncClientWrapper asyncClientWrapper;

  @Before
  public void setUp() {
    asyncClientWrapper = new TestableDynamoDbAsyncClientWrapper(ddbServer.client());
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(Set.of());

    dao = new SanctuariesDao(asyncClientWrapper.get(), "Sanctuaries");

    // create the table
    dao.getTable().createTable().join();
  }

  @Test
  public void when_createSanctuary_then_returnsCreatedAsTrue() {
    // when
    Boolean result = dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID).join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isTrue();
  }

  @Test
  public void given_ddbFailsDuringPutItem_when_createSanctuary_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.PUT_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .putItem(any(PutItemRequest.class));

    // when / then
    CompletionException completionException =
        assertThrows(
            CompletionException.class,
            () -> dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID).join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }

  @Test
  public void when_createSanctuary_twice_then_secondCreationReturnsCreatedAsFalse() {
    // when
    Boolean result =
        dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID)
            .thenCompose(
                firstResult -> dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID))
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isFalse();
  }

  @Test
  public void given_noSanctuaries_when_getSanctuary_then_notPresent() {
    // given / when
    Optional<SanctuaryItem> sanctuary = dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE).join();

    // then
    assertThat(sanctuary).isNotPresent();
  }

  @Test
  public void given_ddbFailsDuringGetItem_when_getSanctuary_then_throwsException() {
    // given
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
        Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.GET_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().build()))
        .when(asyncClientWrapper.get())
        .getItem(any(GetItemRequest.class));

    // when / then
    CompletionException completionException =
        assertThrows(
            CompletionException.class, () -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE).join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }

  @Test
  public void given_sanctuaryOneIsCreated_when_getSanctuary_then_equivalentToSanctuaryOne() {
    // given / when
    Optional<SanctuaryItem> sanctuaryItem =
        dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID)
            .thenCompose(success -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE))
            .join();

    // then
    assertThat(sanctuaryItem).isPresent();
    assertThat(ByteString.copyFrom(sanctuaryItem.get().getSanctuaryGroupId()))
        .isEqualTo(GroupsTestObjects.GROUP_ID_ONE);
  }

  @Test
  public void given_noSanctuaries_when_updateSanctuary_then_throwsResourceNotFoundException() {
    // given / when / then
    SanctuaryItem sanctuary = new SanctuaryItem();
    sanctuary.setSanctuaryGroupId(GroupsTestObjects.GROUP_ID_ONE.toByteArray());
    CompletionException completionException =
        assertThrows(CompletionException.class, () -> dao.updateSanctuary(sanctuary).join());
    assertThat(completionException).hasCauseInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  public void given_sanctuaryOneIsCreated_when_updateSanctuary_withModifications_then_updated() {
    // given / when
    Optional<SanctuaryItem> result =
        dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID)
            .thenCompose(createResult -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE))
            .thenApply(
                sanctuary -> {
                  SanctuaryItem sanctuaryToBeUpdated = sanctuary.get();
                  sanctuaryToBeUpdated.setSanctuaryEnabled(false);
                  sanctuaryToBeUpdated.setSupportContactId(SUPPORT_CONTACT_ID2.toString());
                  return sanctuaryToBeUpdated;
                })
            .thenCompose(sanctuaryToBeUpdated -> dao.updateSanctuary(sanctuaryToBeUpdated))
            .thenCompose(unused -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE))
            .join();

    // then
    assertThat(result).isPresent();
    assertThat(result.get().isSanctuaryEnabled()).isFalse();
    assertThat(result.get().getSupportContactId()).isEqualTo(SUPPORT_CONTACT_ID2.toString());
  }

  @Test
  public void given_sanctuaryOneIsCreated_when_updateSanctuary_withUnmodifiedSanctuaryOne_then_nothing_changed() {
    // given / when
    Optional<SanctuaryItem> result =
        dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID)
            .thenCompose(createResult -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE))
            .thenCompose(sanctuary -> dao.updateSanctuary(sanctuary.get()))
            .thenCompose(unused -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE))
            .join();

    // then
    assertThat(result).isPresent();
    assertThat(result.get().isSanctuaryEnabled()).isTrue();
    assertThat(result.get().getSupportContactId()).isEqualTo(SUPPORT_CONTACT_ID.toString());
  }

  @Test
  public void given_ddbFailsDuringUpdateItem_when_updateSanctuary_then_throwsException() {
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
                dao.createSanctuary(GroupsTestObjects.GROUP_ID_ONE, SUPPORT_CONTACT_ID)
                    .thenCompose(createResult -> dao.getSanctuary(GroupsTestObjects.GROUP_ID_ONE))
                    .thenCompose(sanctuary -> dao.updateSanctuary(sanctuary.get()))
                    .join());
    assertThat(completionException).hasCauseInstanceOf(DynamoDbException.class);
  }
}
