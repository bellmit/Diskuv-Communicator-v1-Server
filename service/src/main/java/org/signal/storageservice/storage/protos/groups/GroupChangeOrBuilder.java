// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Groups.proto

package org.signal.storageservice.storage.protos.groups;

public interface GroupChangeOrBuilder extends
    // @@protoc_insertion_point(interface_extends:signal.GroupChange)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional bytes actions = 1;</code>
   */
  com.google.protobuf.ByteString getActions();

  /**
   * <code>optional bytes serverSignature = 2;</code>
   */
  com.google.protobuf.ByteString getServerSignature();

  /**
   * <code>optional uint32 changeEpoch = 3;</code>
   */
  int getChangeEpoch();
}