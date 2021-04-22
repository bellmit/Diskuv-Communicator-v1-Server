package org.signal.storageservice.controllers;

import com.diskuv.communicatorservice.storage.SanctuaryItem;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.signal.storageservice.providers.ProtocolBufferMediaType;
import org.signal.storageservice.storage.protos.groups.AccessControl;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChanges;
import org.signal.storageservice.storage.protos.groups.Member;
import org.signal.storageservice.util.AuthHelper;
import org.signal.zkgroup.auth.AuthCredential;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// In these tests, the database has a single group containing the following membership:
// * VALID_USER_1 = Administrator        (added in version 0)
// * VALID_USER_2 = Administrator/Member (added as Administrator in version 0, then downgraded to Member in version 4)
// * VALID_USER_3 = Member               (added in version 1)
public class GroupsControllerSanctuaryTest extends BaseGroupsControllerTest {
  private static final ByteString SOME_TITLE = ByteString.copyFromUtf8("Some title");
  private static final ByteString SECOND_TITLE           = ByteString.copyFromUtf8("Second title");
  public static final ByteString  SOME_INVITE_PASSWORD   = ByteString.copyFromUtf8("invite me 1");
  public static final ByteString  SECOND_INVITE_PASSWORD = ByteString.copyFromUtf8("invite me 2");
  private final ByteString groupId = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());
  private final        ByteString userId1 = ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize());
  private final        ByteString userId2 = ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize());
  private final ByteString userId3 = ByteString.copyFrom(validUserThreePresentation.getUuidCiphertext().serialize());
  private final Member adminMember1Version0    = Member.newBuilder()
                                                    .setUserId(userId1)
                                                    .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                                    .setRole(Member.Role.ADMINISTRATOR)
                                                    .build();
  private final Member adminMember2Version0    = Member.newBuilder()
                                                       .setUserId(userId2)
                                                       .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                       .setRole(Member.Role.ADMINISTRATOR)
                                                       .setJoinedAtVersion(0)
                                                       .build();
  private final Member nonAdminMember2Version4 = Member.newBuilder()
                                                       .setUserId(userId2)
                                                       .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                       .setRole(Member.Role.DEFAULT)
                                                       .setJoinedAtVersion(0)
                                                       .build();
  private final Member nonAdminMember3Version1 = Member.newBuilder()
                                                       .setUserId(userId3)
                                                       .setProfileKey(ByteString.copyFrom(validUserThreePresentation.getProfileKeyCiphertext().serialize()))
                                                       .setRole(Member.Role.DEFAULT)
                                                       .setJoinedAtVersion(1)
                                                       .build();

  private final String firstAvatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());

  @Before
  public void setUpSanctuary() {
    SanctuaryItem sanctuaryItem = mock(SanctuaryItem.class);
    ByteString groupId = ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize());
    when(sanctuariesDao.getSanctuary(eq(groupId)))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(sanctuaryItem)));
  }

  @Test
  public void givenVersion5_whenGetSanctuaryGroupAsAdminMember1_thenSeeFullGroup() throws Exception {
    Group groupInServerDatabase = makeGroupVersion5InServerDatabase();
    when(groupsManager.getGroup(eq(groupId)))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(groupInServerDatabase)));

    Group actualGroup = getGroupFromServer(AuthHelper.VALID_USER_AUTH_CREDENTIAL);
    assertThat(actualGroup).isEqualTo(groupInServerDatabase);
  }

  @Test
  public void givenVersion5_whenGetSanctuaryGroupAsNonAdminMember2_thenOnlySeeOwnerAndSelf() throws Exception {
    Group groupInServerDatabase = makeGroupVersion5InServerDatabase();
    when(groupsManager.getGroup(eq(groupId)))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(groupInServerDatabase)));

    Group      actualGroup = getGroupFromServer(AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL);

    assertThat(actualGroup).isEqualTo(Group.newBuilder()
                                           .setPublicKey(groupInServerDatabase.getPublicKey())
                                           .setAccessControl(groupInServerDatabase.getAccessControl())
                                           .setTitle(groupInServerDatabase.getTitle())
                                           .setAvatar(groupInServerDatabase.getAvatar())
                                           .setVersion(5)
                                           .addMembers(adminMember1Version0)
                                           .addMembers(nonAdminMember2Version4)
                                           // No member 3
                                           .build());
  }

  @Test
  public void givenVersion5_whenGetSanctuaryGroupLogsAsAdminMember1_thenSeeAllChanges() throws Exception {
    Group groupInServerDatabase = makeGroupVersion5InServerDatabase();
    when(groupsManager.getGroup(eq(groupId)))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(groupInServerDatabase)));

    List<GroupChanges.GroupChangeState> expectedChanges = makeGroupChangeStatesFromVersion0To5InServerDatabase();
    when(groupsManager.getChangeRecords(eq(groupId), eq(groupInServerDatabase), eq(1), eq(6)))
            .thenReturn(CompletableFuture.completedFuture(expectedChanges));

    GroupChanges receivedChanges = getGroupChangesFromServer(AuthHelper.VALID_USER_AUTH_CREDENTIAL);
    assertThat(GroupChanges.newBuilder().addAllGroupChanges(expectedChanges).build()).isEqualTo(receivedChanges);
  }

  @Test
  public void givenVersion5_whenGetSanctuaryGroupLogsAsNonAdminMember3_thenOnlySeeSelfAndAdminChanges() throws Exception {
    Group groupInServerDatabase = makeGroupVersion5InServerDatabase();
    when(groupsManager.getGroup(eq(groupId)))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(groupInServerDatabase)));

    List<GroupChanges.GroupChangeState> expectedChanges = makeGroupChangeStatesFromVersion0To5InServerDatabase();
    when(groupsManager.getChangeRecords(eq(groupId), eq(groupInServerDatabase), eq(1), eq(6)))
            .thenReturn(CompletableFuture.completedFuture(expectedChanges));

    GroupChanges receivedChanges = getGroupChangesFromServer(AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL);
    assertThat(receivedChanges.getGroupChangesCount()).isEqualTo(5);
    int[]        actualNonTruncatedVersions  = receivedChanges.getGroupChangesList().stream()
                                                              .filter(GroupChanges.GroupChangeState::hasGroupChange)
                                                              .mapToInt(groupChangeState -> groupChangeState.getGroupState().getVersion())
                                                              .toArray();
    // why not v0? we are only asking in getGroupChangesFromServer() for v1+. also v1 is when we (member 3) was added.
    // why not v3? setting of invite link password
    // why not v4? modifying of a different user (member 2)
    assertThat(actualNonTruncatedVersions).isEqualTo(new int[] {1, 2, 5});
  }

  private Group makeGroupVersion0InServerDatabase() {
    return Group.newBuilder()
                .setVersion(0)
                .setTitle(SOME_TITLE)
                .setInviteLinkPassword(SOME_INVITE_PASSWORD)
                .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                .setAccessControl(AccessControl.newBuilder()
                                               .setMembers(AccessControl.AccessRequired.MEMBER)
                                               .setAttributes(AccessControl.AccessRequired.MEMBER))
                .addMembers(adminMember1Version0)
                .addMembers(adminMember2Version0)
                .build();
  }

  private Group makeGroupVersion5InServerDatabase() {
    return Group.newBuilder(makeGroupVersion0InServerDatabase())
                .setVersion(5)
                .setTitle(SECOND_TITLE)
                .setInviteLinkPassword(SECOND_INVITE_PASSWORD)
                .setAvatar(firstAvatar)
                .clearMembers()
                .addMembers(adminMember1Version0)
                .addMembers(nonAdminMember2Version4)
                .addMembers(nonAdminMember3Version1)
                .build();
  }

  private List<GroupChanges.GroupChangeState> makeGroupChangeStatesFromVersion0To5InServerDatabase() {
    List<GroupChanges.GroupChangeState> expectedChanges = new LinkedList<>() {{
      add(GroupChanges.GroupChangeState.newBuilder()
                                       .setGroupChange(GroupChange.newBuilder()
                                                                  .setActions(GroupChange.Actions.newBuilder()
                                                                                                 .addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder()
                                                                                                                                                   .setAdded(nonAdminMember3Version1)
                                                                                                                                                   .build())
                                                                                                 .build()
                                                                                                 .toByteString()))
                                       .setGroupState(Group.newBuilder(makeGroupVersion0InServerDatabase()).setVersion(1).addMembers(nonAdminMember3Version1).build())
                                       .build());

      add(GroupChanges.GroupChangeState.newBuilder()
                                       .setGroupChange(GroupChange.newBuilder()
                                                                  .setActions(GroupChange.Actions.newBuilder()
                                                                                                 .setModifyTitle(GroupChange.Actions.ModifyTitleAction.newBuilder()
                                                                                                                                                      .setTitle(SECOND_TITLE)
                                                                                                                                                      .build())
                                                                                                 .build()
                                                                                                 .toByteString())
                                                                  .build())
                                       .setGroupState(Group.newBuilder(makeGroupVersion0InServerDatabase()).setVersion(2).addMembers(nonAdminMember3Version1).setTitle(SECOND_TITLE).build())
                                       .build());

      add(GroupChanges.GroupChangeState.newBuilder()
                                       .setGroupChange(GroupChange.newBuilder()
                                                                  .setActions(GroupChange.Actions.newBuilder()
                                                                                                 .setModifyInviteLinkPassword(GroupChange.Actions.ModifyInviteLinkPasswordAction.newBuilder()
                                                                                                                                                                                .setInviteLinkPassword(SECOND_INVITE_PASSWORD)
                                                                                                                                                                                .build())
                                                                                                 .build()
                                                                                                 .toByteString())
                                                                  .build())
                                       .setGroupState(Group.newBuilder(makeGroupVersion0InServerDatabase()).setVersion(3).addMembers(nonAdminMember3Version1).setTitle(SECOND_TITLE).setInviteLinkPassword(SECOND_INVITE_PASSWORD).build())
                                       .build());

      add(GroupChanges.GroupChangeState.newBuilder()
                                       .setGroupChange(GroupChange.newBuilder()
                                                                  .setActions(GroupChange.Actions.newBuilder()
                                                                                                 .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder()
                                                                                                                                                                 .setUserId(userId2)
                                                                                                                                                                 .setRole(Member.Role.DEFAULT)
                                                                                                                                                                 .build())
                                                                                                 .build()
                                                                                                 .toByteString())
                                                                  .build())
                                       .setGroupState(Group.newBuilder(makeGroupVersion5InServerDatabase()).setVersion(4).clearAvatar().build())
                                       .build());

      add(GroupChanges.GroupChangeState.newBuilder()
                                       .setGroupChange(GroupChange.newBuilder()
                                                                  .setActions(GroupChange.Actions.newBuilder()
                                                                                                 .setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder()
                                                                                                                                                        .setAvatar(firstAvatar).build())
                                                                                                 .build()
                                                                                                 .toByteString())
                                                                  .build())
                                       .setGroupState(Group.newBuilder(makeGroupVersion5InServerDatabase()).build())
                                       .build());
    }};
    return expectedChanges;
  }

  private Group getGroupFromServer(AuthCredential authCredential) throws IOException {
    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, authCredential))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    byte[] entity = response.readEntity(InputStream.class).readAllBytes();

    assertThat(response.getStatus()).isEqualTo(200);
    return Group.parseFrom(entity);
  }

  private GroupChanges getGroupChangesFromServer(AuthCredential authCredential) throws IOException {
    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/logs/1")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, authCredential))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();

    return GroupChanges.parseFrom(response.readEntity(InputStream.class).readAllBytes());
  }

}
