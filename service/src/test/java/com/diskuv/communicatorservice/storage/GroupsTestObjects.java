package com.diskuv.communicatorservice.storage;

import com.google.protobuf.ByteString;
import org.signal.storageservice.storage.protos.groups.AccessControl;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.Member;

public final class GroupsTestObjects {

  static final ByteString GROUP_ID_ONE = ByteString.copyFromUtf8("group1");
  static final Group GROUP_ONE =
      Group.newBuilder()
          .setTitle(ByteString.copyFromUtf8("title"))
          .setAvatar("avatar")
          .setVersion(27)
          .setAccessControl(
              AccessControl.newBuilder()
                  .setAddFromInviteLink(AccessControl.AccessRequired.MEMBER)
                  .setMembers(AccessControl.AccessRequired.ANY)
                  .build())
          .setDisappearingMessagesTimer(ByteString.copyFromUtf8("disappearing messages timer"))
          .setInviteLinkPassword(ByteString.copyFromUtf8("invite link password"))
          .setPublicKey(ByteString.copyFromUtf8("public key"))
          .addMembers(
              Member.newBuilder()
                  .setJoinedAtVersion(0)
                  .setPresentation(ByteString.copyFromUtf8("presentation"))
                  .setProfileKey(ByteString.copyFromUtf8("profile key"))
                  .setRole(Member.Role.ADMINISTRATOR)
                  .setUserId(ByteString.copyFromUtf8("user id"))
                  .build())
          .build();
  static final GroupChange GROUP_CHANGE_ONE =
      GroupChange.newBuilder().setActions(ByteString.copyFromUtf8("actions1")).build();
  static final GroupChange GROUP_CHANGE_TWO =
      GroupChange.newBuilder().setActions(ByteString.copyFromUtf8("actions2")).build();
  static final GroupChange GROUP_CHANGE_THREE =
      GroupChange.newBuilder().setActions(ByteString.copyFromUtf8("actions3")).build();
}
