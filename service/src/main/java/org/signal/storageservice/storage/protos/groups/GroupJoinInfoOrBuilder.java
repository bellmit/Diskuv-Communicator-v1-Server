// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Groups.proto

package org.signal.storageservice.storage.protos.groups;

public interface GroupJoinInfoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:signal.GroupJoinInfo)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional bytes publicKey = 1;</code>
   */
  com.google.protobuf.ByteString getPublicKey();

  /**
   * <code>optional bytes title = 2;</code>
   */
  com.google.protobuf.ByteString getTitle();

  /**
   * <code>optional string avatar = 3;</code>
   */
  java.lang.String getAvatar();
  /**
   * <code>optional string avatar = 3;</code>
   */
  com.google.protobuf.ByteString
      getAvatarBytes();

  /**
   * <code>optional uint32 memberCount = 4;</code>
   */
  int getMemberCount();

  /**
   * <code>optional .signal.AccessControl.AccessRequired addFromInviteLink = 5;</code>
   */
  int getAddFromInviteLinkValue();
  /**
   * <code>optional .signal.AccessControl.AccessRequired addFromInviteLink = 5;</code>
   */
  org.signal.storageservice.storage.protos.groups.AccessControl.AccessRequired getAddFromInviteLink();

  /**
   * <code>optional uint32 version = 6;</code>
   */
  int getVersion();

  /**
   * <code>optional bool pendingAdminApproval = 7;</code>
   */
  boolean getPendingAdminApproval();
}