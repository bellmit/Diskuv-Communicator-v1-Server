/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.storage;

import com.diskuv.communicatorservice.storage.DDBServerRule;
import com.diskuv.communicatorservice.storage.GroupChangeCache;
import com.diskuv.communicatorservice.storage.GroupLogDao;
import com.diskuv.communicatorservice.storage.GroupsDao;
import com.diskuv.communicatorservice.storage.TestableDynamoDbAsyncClientWrapper;
import com.google.api.core.ApiFutures;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.admin.v2.models.CreateTableRequest;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.signal.storageservice.storage.protos.groups.AccessControl;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions;
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions.ModifyTitleAction;
import org.signal.storageservice.storage.protos.groups.GroupChanges.GroupChangeState;
import org.signal.storageservice.util.AuthHelper;
import org.signal.storageservice.util.Conversions;
import org.signal.zkgroup.groups.GroupPublicParams;
import org.signal.zkgroup.groups.GroupSecretParams;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration.CHECKSUM_SHARED_KEY_SIZE;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class GroupsManagerTest {

  private static final String GROUPS_TABLE_ID     = "groups-table";
  private static final String GROUP_LOGS_TABLE_ID = "group-logs-table";

  /* WAS:
    @Rule
    public final BigtableEmulatorRule bigtableEmulator = BigtableEmulatorRule.create();

    private BigtableDataClient client;
  */
  @Rule public DDBServerRule ddbServer = new DDBServerRule();
  private TestableDynamoDbAsyncClientWrapper asyncClientWrapper;
  private GroupsDao          groupsDao;
  private GroupLogDao        groupLogDao;

  @Before
  public void setup() throws IOException {
    /*
        BigtableTableAdminSettings.Builder tableAdminSettings = BigtableTableAdminSettings.newBuilderForEmulator(bigtableEmulator.getPort()).setProjectId("foo").setInstanceId("bar");
        BigtableTableAdminClient tableAdminClient = BigtableTableAdminClient.create(tableAdminSettings.build());

        BigtableDataSettings.Builder dataSettings = BigtableDataSettings.newBuilderForEmulator(bigtableEmulator.getPort()).setProjectId("foo").setInstanceId("bar");
        client = BigtableDataClient.create(dataSettings.build());

        tableAdminClient.createTable(CreateTableRequest.of(GROUPS_TABLE_ID).addFamily(GroupsTable.FAMILY));
        tableAdminClient.createTable(CreateTableRequest.of(GROUP_LOGS_TABLE_ID).addFamily(GroupLogTable.FAMILY));
    */
    asyncClientWrapper = new TestableDynamoDbAsyncClientWrapper(ddbServer.client());
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(Set.of());

    Cache<ByteString, ByteString> testCache = CacheBuilder.newBuilder().build();
    GroupChangeCache aCache = new GroupChangeCache() {
      @Override
      public int getSuggestedNumberOfCacheCheckingThreads() {
        return 2;
      }

      @Nullable
      @Override
      public byte[] getValueIfPresent(@Nonnull byte[] keyBytes) {
        ByteString bytes = testCache.getIfPresent(ByteString.copyFrom(keyBytes));
        if (bytes == null) {
          return null;
        }
        return bytes.toByteArray();
      }

      @Override
      public void putValue(@Nonnull byte[] keyBytes, @Nonnull byte[] valueBytes) {
        testCache.put(ByteString.copyFrom(keyBytes), ByteString.copyFrom(valueBytes));
      }
    };

    groupsDao   = new GroupsDao(asyncClientWrapper.get(), GROUPS_TABLE_ID, new byte[CHECKSUM_SHARED_KEY_SIZE]);
    groupLogDao = new GroupLogDao(asyncClientWrapper.get(), GROUP_LOGS_TABLE_ID, Optional.of(aCache));

    // create the tables
    groupsDao.getTable().createTable().join();
    groupLogDao.getTable().createTable().join();
  }

  @Test
  public void testCreateGroup() throws Exception {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setVersion(0)
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("Some avatar")
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .build();

    CompletableFuture<Boolean> result = groupsManager.createGroup(groupId, group);
    assertTrue(result.get());

    // WAS: Row row = client.readRow(GROUPS_TABLE_ID, groupId);
    Row row = clientReadRow(GROUPS_TABLE_ID, groupId);
    List<RowCell> versionCells= row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_VERSION);

    assertThat(versionCells.size()).isEqualTo(1);
    assertThat(versionCells.get(0).getValue().toStringUtf8()).isEqualTo("0");

    List<RowCell> dataCells = row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_GROUP_DATA);

    assertThat(dataCells.size()).isEqualTo(1);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isEqualTo(group);
  }

  @Test
  public void testCreateGroupConflict() throws Exception {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setVersion(0)
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("Some avatar")
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .build();

    CompletableFuture<Boolean> result = groupsManager.createGroup(groupId, group);
    assertTrue(result.get());

    Group conflictingGroup = Group.newBuilder()
                                  .setVersion(0)
                                  .setTitle(ByteString.copyFromUtf8("Another title"))
                                  .setAvatar("Another avatar")
                                  .setAccessControl(AccessControl.newBuilder()
                                                                 .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                 .setAttributes(AccessControl.AccessRequired.MEMBER))
                                  .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                                  .build();

    CompletableFuture<Boolean> conflicting = groupsManager.createGroup(groupId, group);
    assertFalse(conflicting.get());

    // WAS: Row row = client.readRow(GROUPS_TABLE_ID, groupId);
    Row row = clientReadRow(GROUPS_TABLE_ID, groupId);
    List<RowCell> versionCells= row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_VERSION);

    assertThat(versionCells.size()).isEqualTo(1);
    assertThat(versionCells.get(0).getValue().toStringUtf8()).isEqualTo("0");

    List<RowCell> dataCells = row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_GROUP_DATA);

    assertThat(dataCells.size()).isEqualTo(1);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isEqualTo(group);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isNotEqualTo(conflictingGroup);
  }

  @Test
  public void testUpdateGroup() throws Exception {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setVersion(0)
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("Some avatar")
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .build();

    CompletableFuture<Boolean> result = groupsManager.createGroup(groupId, group);
    assertTrue(result.get());

    Group updated = group.toBuilder()
                         .setVersion(1)
                         .setTitle(ByteString.copyFromUtf8("Updated title"))
                         .build();

    CompletableFuture<Optional<Group>> update = groupsManager.updateGroup(groupId, updated);
    assertThat(update.get()).isEmpty();

    // WAS: Row row = client.readRow(GROUPS_TABLE_ID, groupId);
    Row row = clientReadRow(GROUPS_TABLE_ID, groupId);
    List<RowCell> versionCells= row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_VERSION);

    assertThat(versionCells.size()).isEqualTo(1);
    assertThat(versionCells.get(0).getValue().toStringUtf8()).isEqualTo("1");

    List<RowCell> dataCells = row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_GROUP_DATA);

    assertThat(dataCells.size()).isEqualTo(1);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isEqualTo(updated);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isNotEqualTo(group);
  }

  @Test
  public void testUpdateStaleGroup() throws Exception {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setVersion(0)
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("Some avatar")
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .build();

    CompletableFuture<Boolean> result = groupsManager.createGroup(groupId, group);
    assertTrue(result.get());

    Group updated = group.toBuilder()
                         .setVersion(0)
                         .setTitle(ByteString.copyFromUtf8("Updated title"))
                         .build();

    CompletableFuture<Optional<Group>> update = groupsManager.updateGroup(groupId, updated);
    assertThat(update.get()).isPresent();
    assertThat(update.get().get()).isEqualTo(group);

    // WAS: Row row = client.readRow(GROUPS_TABLE_ID, groupId);
    Row row = clientReadRow(GROUPS_TABLE_ID, groupId);
    List<RowCell> versionCells= row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_VERSION);

    assertThat(versionCells.size()).isEqualTo(1);
    assertThat(versionCells.get(0).getValue().toStringUtf8()).isEqualTo("0");

    List<RowCell> dataCells = row.getCells(GroupsTable.FAMILY, GroupsTable.COLUMN_GROUP_DATA);

    assertThat(dataCells.size()).isEqualTo(1);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isEqualTo(group);
    assertThat(Group.parseFrom(dataCells.get(0).getValue())).isNotEqualTo(updated);
  }

  @Test
  public void testGetGroup() throws Exception {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setVersion(0)
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("Some avatar")
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .build();

    CompletableFuture<Boolean> result = groupsManager.createGroup(groupId, group);
    assertTrue(result.get());

    CompletableFuture<Optional<Group>> retrieved = groupsManager.getGroup(groupId);
    assertThat(retrieved.get().isPresent()).isTrue();
    assertThat(retrieved.get().get()).isEqualTo(group);
  }

  @Test
  public void testGetGroupNotFound() throws Exception {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setVersion(0)
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("Some avatar")
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .build();

    CompletableFuture<Boolean> result = groupsManager.createGroup(groupId, group);
    assertTrue(result.get());

    CompletableFuture<Optional<Group>> retrieved = groupsManager.getGroup(ByteString.copyFrom(GroupSecretParams.generate().getPublicParams().getGroupIdentifier().serialize()));
    assertThat(retrieved.get().isPresent()).isFalse();
    assertThat(retrieved.get().isEmpty()).isTrue();
  }


  @Test
  public void testReadError() {
    /* WAS ...

        BigtableDataClient client = mock(BigtableDataClient.class);
        when(client.readRowAsync(anyString(), any(ByteString.class))).thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("Bad news")));

        GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    */
    asyncClientWrapper.delegateToRealDynamoDBOperationsExcept(
            Set.of(TestableDynamoDbAsyncClientWrapper.DynamoDBOperation.GET_ITEM));
    doAnswer(o -> CompletableFuture.failedFuture(DynamoDbException.builder().message("Bad news").build()))
            .when(asyncClientWrapper.get())
            .getItem(any(GetItemRequest.class));
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);

    try {
      groupsManager.getGroup(ByteString.copyFrom(new byte[16])).get();
      throw new AssertionError();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      assertThat(e.getCause().getMessage()).isEqualTo("Bad news");
    }
  }

  @Test
  public void testAppendLog() throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Actions actions = Actions.newBuilder()
                             .setModifyTitle(ModifyTitleAction.newBuilder()
                                                              .setTitle(ByteString.copyFromUtf8("Some new title")))
                             .build();

    GroupChange change = GroupChange.newBuilder()
                                    .setActions(actions.toByteString())
                                    .setServerSignature(ByteString.copyFrom(AuthHelper.GROUPS_SERVER_KEY.sign(actions.toByteArray()).serialize()))
                                    .build();

    Group groupState = Group.newBuilder()
                            .setTitle(ByteString.copyFromUtf8("Some new title"))
                            .setAvatar("some avatar")
                            .build();

    CompletableFuture<Boolean> insert = groupsManager.appendChangeRecord(groupId, 1, change, groupState);
    assertTrue(insert.get());

    // WAS: Row row = client.readRow(GROUP_LOGS_TABLE_ID, groupId.concat(ByteString.copyFromUtf8("#")).concat(ByteString.copyFrom(Conversions.intToByteArray(1))));
    Row row = clientReadRow(GROUP_LOGS_TABLE_ID, groupId.concat(ByteString.copyFromUtf8("#")).concat(ByteString.copyFrom(Conversions.intToByteArray(1))));

    List<RowCell> versionCells = row.getCells(GroupLogTable.FAMILY, GroupLogTable.COLUMN_VERSION);

    assertThat(versionCells.size()).isEqualTo(1);
    assertThat(versionCells.get(0).getValue().toStringUtf8()).isEqualTo("1");

    List<RowCell> dataCells = row.getCells(GroupLogTable.FAMILY, GroupLogTable.COLUMN_CHANGE);

    assertThat(dataCells.size()).isEqualTo(1);
    assertThat(GroupChange.parseFrom(dataCells.get(0).getValue())).isEqualTo(change);

    List<RowCell> groupStateCells = row.getCells(GroupLogTable.FAMILY, GroupLogTable.COLUMN_STATE);

    assertThat(groupStateCells.size()).isEqualTo(1);
    assertThat(Group.parseFrom(groupStateCells.get(0).getValue())).isEqualTo(groupState);
  }

  @Test
  public void testQueryLog() throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    // WAS: GroupsManager groupsManager = new GroupsManager(client, GROUPS_TABLE_ID, GROUP_LOGS_TABLE_ID);
    GroupsManager         groupsManager = new GroupsManager(groupsDao, groupLogDao);
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();
    ByteString        groupId           = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());

    Group latestGroupState = null;
    for (int i=1;i<2000;i++) {
      Actions actions = Actions.newBuilder()
                               .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                .setTitle(ByteString.copyFromUtf8("Some new title " + i)))
                               .build();

      GroupChange change = GroupChange.newBuilder()
                                      .setActions(actions.toByteString())
                                      .setServerSignature(ByteString.copyFrom(AuthHelper.GROUPS_SERVER_KEY.sign(actions.toByteArray()).serialize()))
                                      .build();

      Group groupState = Group.newBuilder()
                              .setTitle(ByteString.copyFromUtf8("Some new title " + i))
                              .build();
      latestGroupState = groupState;

      CompletableFuture<Boolean> insert = groupsManager.appendChangeRecord(groupId, i, change, groupState);
      assertTrue(insert.get());
    }

    assertThat(latestGroupState).isNotNull();
    List<GroupChangeState> changes = groupsManager.getChangeRecords(groupId, latestGroupState, 1, 20).get();
    assertThat(changes.size()).isEqualTo(19);

    for (int i=1;i<20;i++) {
      assertThat(Actions.parseFrom(changes.get(i-1).getGroupChange().getActions()).getModifyTitle().getTitle().toStringUtf8()).isEqualTo("Some new title " + i);
      assertThat(changes.get(i-1).getGroupState().getTitle().toStringUtf8()).isEqualTo("Some new title " + i);
    }

    changes = groupsManager.getChangeRecords(groupId, latestGroupState, 10, 200).get();
    assertThat(changes.size()).isEqualTo(190);

    for (int i=10;i<200;i++) {
      assertThat(Actions.parseFrom(changes.get(i-10).getGroupChange().getActions()).getModifyTitle().getTitle().toStringUtf8()).isEqualTo("Some new title " + i);
      assertThat(changes.get(i-10).getGroupState().getTitle().toStringUtf8()).isEqualTo("Some new title " + i);
    }
  }


  /**
   * Provided to allow Signal's test code to work without much modification.
   */
  private Row clientReadRow(String tableName, ByteString groupId) {
    if (GROUPS_TABLE_ID.equals(tableName)) {
      Optional<Group> group = groupsDao.getGroup(groupId).join();
      if (group.isEmpty()) {
        return Row.create(groupId, List.of());
      }
      List<RowCell> versionCells =
              List.of(
                      RowCell.create(
                              GroupsTable.FAMILY,
                              ByteString.copyFromUtf8(GroupsTable.COLUMN_VERSION),
                              0L,
                              List.of(),
                              ByteString.copyFromUtf8(Integer.toString(group.get().getVersion()))));
      List<RowCell> dataCells =
              List.of(
                      RowCell.create(
                              GroupsTable.FAMILY,
                              ByteString.copyFromUtf8(GroupsTable.COLUMN_GROUP_DATA),
                              0L,
                              List.of(),
                              group.get().toByteString()));
      return Row.create(
          groupId,
          Ordering.from(RowCell.compareByNative())
              .sortedCopy(
                  ImmutableList.<RowCell>builder().addAll(versionCells).addAll(dataCells).build()));
    } else if (GROUP_LOGS_TABLE_ID.equals(tableName)) {
      // format: groupId + '#' + Conversions.intToByteArray(version)
      ByteString versionPart = groupId.substring(groupId.size() - Conversions.intToByteArray(1).length);
      ByteString groupIdPart = groupId.substring(0, groupId.size() - versionPart.size() - "#".getBytes(StandardCharsets.UTF_8).length);
      int version = Conversions.byteArrayToInt(versionPart.toByteArray());
      List<GroupChangeState> changeStates =
          groupLogDao.getRecordsFromVersion(groupIdPart, version, version + 1).join();
      List<RowCell> versionCells =
          changeStates.stream()
              .map(
                  groupChangeState ->
                      RowCell.create(
                          GroupLogTable.FAMILY,
                          ByteString.copyFromUtf8(GroupLogTable.COLUMN_VERSION),
                          0L,
                          List.of(),
                          ByteString.copyFromUtf8(Integer.toString(version))))
              .collect(Collectors.toList());
      List<RowCell> groupChangeCells =
          changeStates.stream()
              .map(
                  groupChangeState ->
                      RowCell.create(
                          GroupLogTable.FAMILY,
                          ByteString.copyFromUtf8(GroupLogTable.COLUMN_CHANGE),
                          0L,
                          List.of(),
                          groupChangeState.getGroupChange().toByteString()))
              .collect(Collectors.toList());
      List<RowCell> groupStateCells =
          changeStates.stream()
              .map(
                  groupChangeState ->
                      RowCell.create(
                          GroupLogTable.FAMILY,
                          ByteString.copyFromUtf8(GroupLogTable.COLUMN_STATE),
                          0L,
                          List.of(),
                          groupChangeState.getGroupState().toByteString()))
              .collect(Collectors.toList());
      return Row.create(
          groupIdPart,
          Ordering.from(RowCell.compareByNative())
              .sortedCopy(
                  ImmutableList.<RowCell>builder()
                      .addAll(versionCells)
                      .addAll(groupChangeCells)
                      .addAll(groupStateCells)
                      .build()));
    }
    throw new IllegalArgumentException("Unsupported table: " + tableName);
  }
}
