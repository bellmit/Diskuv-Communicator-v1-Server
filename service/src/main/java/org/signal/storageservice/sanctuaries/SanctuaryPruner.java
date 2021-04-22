package org.signal.storageservice.sanctuaries;

import com.google.protobuf.InvalidProtocolBufferException;
import org.signal.storageservice.auth.GroupUser;
import org.signal.storageservice.groups.GroupAuth;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChanges;
import org.signal.storageservice.storage.protos.groups.Member;

import java.util.List;
import java.util.stream.Collectors;

public class SanctuaryPruner {
  public static Group pruneGroup(GroupUser user, Group group, boolean sanctuary) {
    if (!sanctuary) return group;
    if (GroupAuth.isAdminstrator(user, group)) return group;

    // We have a sanctuary group with the user being a non-administrator. Prune!
    // -------------------------------------------------------------------------

    return pruneSanctuaryGroupForNonOwner(group, user);
  }

  public static List<GroupChanges.GroupChangeState> pruneChangeRecords(GroupUser user, Group group, boolean sanctuary, List<GroupChanges.GroupChangeState> changeRecords) {
    if (!sanctuary) return changeRecords;
    if (GroupAuth.isAdminstrator(user, group)) return changeRecords;

    // We have a sanctuary group with the user being a non-administrator. Prune!
    // -------------------------------------------------------------------------

    return changeRecords.stream()
        .map(
            // Truncate the change from the change record if it shouldn't be seen.
            // We still KEEP THE CHANGE RECORD so that the client (mobile app) can keep
            // track of its position within the change log using a monotonically increasing
            // version number.
            groupChangeState -> {
              if (!groupChangeState.hasGroupChange()) {
                // Nothing to do
                return groupChangeState;
              }

              GroupChange groupChange = groupChangeState.getGroupChange();
              GroupChange.Actions actions;
              try {
                actions = GroupChange.Actions.parseFrom(groupChange.getActions());
              } catch (InvalidProtocolBufferException e) {
                // Can't parse, truncate the change record
                return truncateChangeRecord(groupChangeState);
              }

              // More actions may be added over time by Signal or Diskuv! So only export a change
              // if it is part of a _whitelist_.
              // Notable exceptions to the whitelist:
              // * NO_PROFILE_KEYS. Server-side there is no way to distinguish which profile key "presentation" belongs to what user, because the presentation is encrypted with the group master key. So we _never_ return profile keys in a sanctuary group. Instead sanctuary members exchange one-on-one keys like any Signal user outside of a group.
              // * NO_INVITE_LINK. Non sanctuary owners SHOULD NOT be capable of sending an invite link password (of course the user may simply share the old invite link given to them ... see discussion in Diskuv-Communicator-Android/diskuv-changes/2021-04-21-sanctuary-visibility-pruning)

              GroupChange.Actions.Builder builder = GroupChange.Actions.newBuilder();
              if (actions.hasModifyAddFromInviteLinkAccess())  builder.setModifyAddFromInviteLinkAccess(actions.getModifyAddFromInviteLinkAccess());
              // NO_INVITE_LINK. if (actions.hasModifyInviteLinkPassword()) builder.setModifyInviteLinkPassword(actions.getSomething());
              // [unknown fields can't be part of a whitelist!] builder.setUnknownFields(actions.getSomething())
              if (actions.hasModifyAttributesAccess())         builder.setModifyAttributesAccess(actions.getModifyAttributesAccess());
              if (actions.hasModifyAvatar())                   builder.setModifyAvatar(actions.getModifyAvatar());
              if (actions.hasModifyDisappearingMessageTimer()) builder.setModifyDisappearingMessageTimer(actions.getModifyDisappearingMessageTimer());
              if (actions.hasModifyMemberAccess())             builder.setModifyMemberAccess(actions.getModifyMemberAccess());
              if (actions.hasModifyTitle())                    builder.setModifyTitle(actions.getModifyTitle());
              builder.setSourceUuid(actions.getSourceUuid());
              builder.setVersion(actions.getVersion());

              builder.addAllAddMembers(                        actions.getAddMembersList().stream().filter(                        action -> user.isMember(action.getAdded().getUserId(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllAddMembersPendingAdminApproval(    actions.getAddMembersPendingAdminApprovalList().stream().filter(    action -> user.isMember(action.getAdded().getUserId(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllAddMembersPendingProfileKey(       actions.getAddMembersPendingProfileKeyList().stream().filter(       action -> user.isMember(action.getAdded().getMember(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllDeleteMembers(                     actions.getDeleteMembersList().stream().filter(                     action -> user.isMember(action.getDeletedUserId(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllDeleteMembersPendingAdminApproval( actions.getDeleteMembersPendingAdminApprovalList().stream().filter( action -> user.isMember(action.getDeletedUserId(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllDeleteMembersPendingProfileKey(    actions.getDeleteMembersPendingProfileKeyList().stream().filter(    action -> user.isMember(action.getDeletedUserId(), group.getPublicKey())).collect(Collectors.toList()));
              // NO_PROFILE_KEYS. builder.addAllModifyMemberProfileKeys(           actions.getModifyMemberProfileKeysList().stream().filter(           action -> user.isMember(action.getSomething(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllModifyMemberRoles(                 actions.getModifyMemberRolesList().stream().filter(                 action -> user.isMember(action.getUserId(), group.getPublicKey())).collect(Collectors.toList()));
              builder.addAllPromoteMembersPendingAdminApproval(actions.getPromoteMembersPendingAdminApprovalList().stream().filter(action -> user.isMember(action.getUserId(), group.getPublicKey())).collect(Collectors.toList()));
              // NO_PROFILE_KEYS. builder.addAllPromoteMembersPendingProfileKey(   actions.getPromoteMembersPendingProfileKeyList().stream().filter(   action -> user.isMember(action.getSomething(), group.getPublicKey())).collect(Collectors.toList()));

              GroupChange.Actions whitelist = builder.build();
              if (!whitelist.equals(actions)) {
                // Not whitelisted, so truncate the change record
                return truncateChangeRecord(groupChangeState);
              }
              // Whitelisted, so continue with the change as-is
              return groupChangeState;
            })
        .map(
            // Prune the snapshot of the group at the time of the change
            groupChangeState -> {
              if (!groupChangeState.hasGroupState()) {
                return groupChangeState;
              }
              return GroupChanges.GroupChangeState.newBuilder(groupChangeState)
                  .setGroupState(pruneSanctuaryGroupForNonOwner(groupChangeState.getGroupState(), user))
                  .build();
            })
        .collect(Collectors.toList());
  }

  private static GroupChanges.GroupChangeState truncateChangeRecord(GroupChanges.GroupChangeState groupChangeState) {
    // Remove the "change", but keep the modified "group"
    return GroupChanges.GroupChangeState.newBuilder(groupChangeState).clearGroupChange().build();
  }

  private static Group pruneSanctuaryGroupForNonOwner(Group group, GroupUser user) {
    Group.Builder builder = Group.newBuilder(group);

    // Non sanctuary owners SHOULD NOT be capable of sending an invite link password (of course the user may simply share the old invite link given to them ... see discussion in Diskuv-Communicator-Android/diskuv-changes/2021-04-21-sanctuary-visibility-pruning)
    builder.clearInviteLinkPassword();

    // Non sanctuary owners MUST NOT be able to see members except the sanctuary owner
    List<Member> owners = getOwnersAndSelf(group, user, builder.getMembersList());
    builder.clearMembers();
    builder.addAllMembers(owners);

    // Non sanctuary owners MUST NOT be able to see members except the sanctuary owner
    builder.clearMembersPendingAdminApproval();

    // Non sanctuary owners MUST NOT be able to see members except the sanctuary owner
    builder.clearMembersPendingProfileKey();

    return builder.build();
  }

  private static List<Member> getOwnersAndSelf(Group group, GroupUser user, List<Member> membersList) {
    if (membersList == null)    return null;
    return membersList.stream()
                      .filter(member -> member.getRole() == Member.Role.ADMINISTRATOR || user.isMember(member, group.getPublicKey()))
                      .collect(Collectors.toList());
  }

}
