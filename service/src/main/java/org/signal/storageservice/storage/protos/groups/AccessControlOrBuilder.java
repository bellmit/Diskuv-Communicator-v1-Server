// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Groups.proto

package org.signal.storageservice.storage.protos.groups;

public interface AccessControlOrBuilder extends
    // @@protoc_insertion_point(interface_extends:signal.AccessControl)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional .signal.AccessControl.AccessRequired attributes = 1;</code>
   */
  int getAttributesValue();
  /**
   * <code>optional .signal.AccessControl.AccessRequired attributes = 1;</code>
   */
  org.signal.storageservice.storage.protos.groups.AccessControl.AccessRequired getAttributes();

  /**
   * <code>optional .signal.AccessControl.AccessRequired members = 2;</code>
   */
  int getMembersValue();
  /**
   * <code>optional .signal.AccessControl.AccessRequired members = 2;</code>
   */
  org.signal.storageservice.storage.protos.groups.AccessControl.AccessRequired getMembers();

  /**
   * <code>optional .signal.AccessControl.AccessRequired addFromInviteLink = 3;</code>
   */
  int getAddFromInviteLinkValue();
  /**
   * <code>optional .signal.AccessControl.AccessRequired addFromInviteLink = 3;</code>
   */
  org.signal.storageservice.storage.protos.groups.AccessControl.AccessRequired getAddFromInviteLink();
}
