// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Groups.proto

package org.signal.storageservice.storage.protos.groups;

/**
 * Protobuf type {@code signal.AvatarUploadAttributes}
 */
public  final class AvatarUploadAttributes extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:signal.AvatarUploadAttributes)
    AvatarUploadAttributesOrBuilder {
  // Use AvatarUploadAttributes.newBuilder() to construct.
  private AvatarUploadAttributes(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private AvatarUploadAttributes() {
    key_ = "";
    credential_ = "";
    acl_ = "";
    algorithm_ = "";
    date_ = "";
    policy_ = "";
    signature_ = "";
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
  }
  private AvatarUploadAttributes(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    int mutable_bitField0_ = 0;
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          default: {
            if (!input.skipField(tag)) {
              done = true;
            }
            break;
          }
          case 10: {
            java.lang.String s = input.readStringRequireUtf8();

            key_ = s;
            break;
          }
          case 18: {
            java.lang.String s = input.readStringRequireUtf8();

            credential_ = s;
            break;
          }
          case 26: {
            java.lang.String s = input.readStringRequireUtf8();

            acl_ = s;
            break;
          }
          case 34: {
            java.lang.String s = input.readStringRequireUtf8();

            algorithm_ = s;
            break;
          }
          case 42: {
            java.lang.String s = input.readStringRequireUtf8();

            date_ = s;
            break;
          }
          case 50: {
            java.lang.String s = input.readStringRequireUtf8();

            policy_ = s;
            break;
          }
          case 58: {
            java.lang.String s = input.readStringRequireUtf8();

            signature_ = s;
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return org.signal.storageservice.storage.protos.groups.GroupProtos.internal_static_signal_AvatarUploadAttributes_descriptor;
  }

  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return org.signal.storageservice.storage.protos.groups.GroupProtos.internal_static_signal_AvatarUploadAttributes_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.class, org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.Builder.class);
  }

  public static final int KEY_FIELD_NUMBER = 1;
  private volatile java.lang.Object key_;
  /**
   * <code>optional string key = 1;</code>
   */
  public java.lang.String getKey() {
    java.lang.Object ref = key_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      key_ = s;
      return s;
    }
  }
  /**
   * <code>optional string key = 1;</code>
   */
  public com.google.protobuf.ByteString
      getKeyBytes() {
    java.lang.Object ref = key_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      key_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int CREDENTIAL_FIELD_NUMBER = 2;
  private volatile java.lang.Object credential_;
  /**
   * <code>optional string credential = 2;</code>
   */
  public java.lang.String getCredential() {
    java.lang.Object ref = credential_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      credential_ = s;
      return s;
    }
  }
  /**
   * <code>optional string credential = 2;</code>
   */
  public com.google.protobuf.ByteString
      getCredentialBytes() {
    java.lang.Object ref = credential_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      credential_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int ACL_FIELD_NUMBER = 3;
  private volatile java.lang.Object acl_;
  /**
   * <code>optional string acl = 3;</code>
   */
  public java.lang.String getAcl() {
    java.lang.Object ref = acl_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      acl_ = s;
      return s;
    }
  }
  /**
   * <code>optional string acl = 3;</code>
   */
  public com.google.protobuf.ByteString
      getAclBytes() {
    java.lang.Object ref = acl_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      acl_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int ALGORITHM_FIELD_NUMBER = 4;
  private volatile java.lang.Object algorithm_;
  /**
   * <code>optional string algorithm = 4;</code>
   */
  public java.lang.String getAlgorithm() {
    java.lang.Object ref = algorithm_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      algorithm_ = s;
      return s;
    }
  }
  /**
   * <code>optional string algorithm = 4;</code>
   */
  public com.google.protobuf.ByteString
      getAlgorithmBytes() {
    java.lang.Object ref = algorithm_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      algorithm_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int DATE_FIELD_NUMBER = 5;
  private volatile java.lang.Object date_;
  /**
   * <code>optional string date = 5;</code>
   */
  public java.lang.String getDate() {
    java.lang.Object ref = date_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      date_ = s;
      return s;
    }
  }
  /**
   * <code>optional string date = 5;</code>
   */
  public com.google.protobuf.ByteString
      getDateBytes() {
    java.lang.Object ref = date_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      date_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int POLICY_FIELD_NUMBER = 6;
  private volatile java.lang.Object policy_;
  /**
   * <code>optional string policy = 6;</code>
   */
  public java.lang.String getPolicy() {
    java.lang.Object ref = policy_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      policy_ = s;
      return s;
    }
  }
  /**
   * <code>optional string policy = 6;</code>
   */
  public com.google.protobuf.ByteString
      getPolicyBytes() {
    java.lang.Object ref = policy_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      policy_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int SIGNATURE_FIELD_NUMBER = 7;
  private volatile java.lang.Object signature_;
  /**
   * <code>optional string signature = 7;</code>
   */
  public java.lang.String getSignature() {
    java.lang.Object ref = signature_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs =
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      signature_ = s;
      return s;
    }
  }
  /**
   * <code>optional string signature = 7;</code>
   */
  public com.google.protobuf.ByteString
      getSignatureBytes() {
    java.lang.Object ref = signature_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      signature_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  private byte memoizedIsInitialized = -1;
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (!getKeyBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, key_);
    }
    if (!getCredentialBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, credential_);
    }
    if (!getAclBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 3, acl_);
    }
    if (!getAlgorithmBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 4, algorithm_);
    }
    if (!getDateBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 5, date_);
    }
    if (!getPolicyBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 6, policy_);
    }
    if (!getSignatureBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 7, signature_);
    }
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getKeyBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, key_);
    }
    if (!getCredentialBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, credential_);
    }
    if (!getAclBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, acl_);
    }
    if (!getAlgorithmBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(4, algorithm_);
    }
    if (!getDateBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(5, date_);
    }
    if (!getPolicyBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(6, policy_);
    }
    if (!getSignatureBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(7, signature_);
    }
    memoizedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes)) {
      return super.equals(obj);
    }
    org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes other = (org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes) obj;

    boolean result = true;
    result = result && getKey()
        .equals(other.getKey());
    result = result && getCredential()
        .equals(other.getCredential());
    result = result && getAcl()
        .equals(other.getAcl());
    result = result && getAlgorithm()
        .equals(other.getAlgorithm());
    result = result && getDate()
        .equals(other.getDate());
    result = result && getPolicy()
        .equals(other.getPolicy());
    result = result && getSignature()
        .equals(other.getSignature());
    return result;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptorForType().hashCode();
    hash = (37 * hash) + KEY_FIELD_NUMBER;
    hash = (53 * hash) + getKey().hashCode();
    hash = (37 * hash) + CREDENTIAL_FIELD_NUMBER;
    hash = (53 * hash) + getCredential().hashCode();
    hash = (37 * hash) + ACL_FIELD_NUMBER;
    hash = (53 * hash) + getAcl().hashCode();
    hash = (37 * hash) + ALGORITHM_FIELD_NUMBER;
    hash = (53 * hash) + getAlgorithm().hashCode();
    hash = (37 * hash) + DATE_FIELD_NUMBER;
    hash = (53 * hash) + getDate().hashCode();
    hash = (37 * hash) + POLICY_FIELD_NUMBER;
    hash = (53 * hash) + getPolicy().hashCode();
    hash = (37 * hash) + SIGNATURE_FIELD_NUMBER;
    hash = (53 * hash) + getSignature().hashCode();
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code signal.AvatarUploadAttributes}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:signal.AvatarUploadAttributes)
      org.signal.storageservice.storage.protos.groups.AvatarUploadAttributesOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return org.signal.storageservice.storage.protos.groups.GroupProtos.internal_static_signal_AvatarUploadAttributes_descriptor;
    }

    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return org.signal.storageservice.storage.protos.groups.GroupProtos.internal_static_signal_AvatarUploadAttributes_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.class, org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.Builder.class);
    }

    // Construct using org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    public Builder clear() {
      super.clear();
      key_ = "";

      credential_ = "";

      acl_ = "";

      algorithm_ = "";

      date_ = "";

      policy_ = "";

      signature_ = "";

      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return org.signal.storageservice.storage.protos.groups.GroupProtos.internal_static_signal_AvatarUploadAttributes_descriptor;
    }

    public org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes getDefaultInstanceForType() {
      return org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.getDefaultInstance();
    }

    public org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes build() {
      org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes buildPartial() {
      org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes result = new org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes(this);
      result.key_ = key_;
      result.credential_ = credential_;
      result.acl_ = acl_;
      result.algorithm_ = algorithm_;
      result.date_ = date_;
      result.policy_ = policy_;
      result.signature_ = signature_;
      onBuilt();
      return result;
    }

    public Builder clone() {
      return (Builder) super.clone();
    }
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        Object value) {
      return (Builder) super.setField(field, value);
    }
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return (Builder) super.clearField(field);
    }
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return (Builder) super.clearOneof(oneof);
    }
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, Object value) {
      return (Builder) super.setRepeatedField(field, index, value);
    }
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        Object value) {
      return (Builder) super.addRepeatedField(field, value);
    }
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes) {
        return mergeFrom((org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes other) {
      if (other == org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes.getDefaultInstance()) return this;
      if (!other.getKey().isEmpty()) {
        key_ = other.key_;
        onChanged();
      }
      if (!other.getCredential().isEmpty()) {
        credential_ = other.credential_;
        onChanged();
      }
      if (!other.getAcl().isEmpty()) {
        acl_ = other.acl_;
        onChanged();
      }
      if (!other.getAlgorithm().isEmpty()) {
        algorithm_ = other.algorithm_;
        onChanged();
      }
      if (!other.getDate().isEmpty()) {
        date_ = other.date_;
        onChanged();
      }
      if (!other.getPolicy().isEmpty()) {
        policy_ = other.policy_;
        onChanged();
      }
      if (!other.getSignature().isEmpty()) {
        signature_ = other.signature_;
        onChanged();
      }
      onChanged();
      return this;
    }

    public final boolean isInitialized() {
      return true;
    }

    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object key_ = "";
    /**
     * <code>optional string key = 1;</code>
     */
    public java.lang.String getKey() {
      java.lang.Object ref = key_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        key_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string key = 1;</code>
     */
    public com.google.protobuf.ByteString
        getKeyBytes() {
      java.lang.Object ref = key_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        key_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string key = 1;</code>
     */
    public Builder setKey(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      key_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string key = 1;</code>
     */
    public Builder clearKey() {

      key_ = getDefaultInstance().getKey();
      onChanged();
      return this;
    }
    /**
     * <code>optional string key = 1;</code>
     */
    public Builder setKeyBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      key_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object credential_ = "";
    /**
     * <code>optional string credential = 2;</code>
     */
    public java.lang.String getCredential() {
      java.lang.Object ref = credential_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        credential_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string credential = 2;</code>
     */
    public com.google.protobuf.ByteString
        getCredentialBytes() {
      java.lang.Object ref = credential_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        credential_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string credential = 2;</code>
     */
    public Builder setCredential(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      credential_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string credential = 2;</code>
     */
    public Builder clearCredential() {

      credential_ = getDefaultInstance().getCredential();
      onChanged();
      return this;
    }
    /**
     * <code>optional string credential = 2;</code>
     */
    public Builder setCredentialBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      credential_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object acl_ = "";
    /**
     * <code>optional string acl = 3;</code>
     */
    public java.lang.String getAcl() {
      java.lang.Object ref = acl_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        acl_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string acl = 3;</code>
     */
    public com.google.protobuf.ByteString
        getAclBytes() {
      java.lang.Object ref = acl_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        acl_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string acl = 3;</code>
     */
    public Builder setAcl(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      acl_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string acl = 3;</code>
     */
    public Builder clearAcl() {

      acl_ = getDefaultInstance().getAcl();
      onChanged();
      return this;
    }
    /**
     * <code>optional string acl = 3;</code>
     */
    public Builder setAclBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      acl_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object algorithm_ = "";
    /**
     * <code>optional string algorithm = 4;</code>
     */
    public java.lang.String getAlgorithm() {
      java.lang.Object ref = algorithm_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        algorithm_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string algorithm = 4;</code>
     */
    public com.google.protobuf.ByteString
        getAlgorithmBytes() {
      java.lang.Object ref = algorithm_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        algorithm_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string algorithm = 4;</code>
     */
    public Builder setAlgorithm(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      algorithm_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string algorithm = 4;</code>
     */
    public Builder clearAlgorithm() {

      algorithm_ = getDefaultInstance().getAlgorithm();
      onChanged();
      return this;
    }
    /**
     * <code>optional string algorithm = 4;</code>
     */
    public Builder setAlgorithmBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      algorithm_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object date_ = "";
    /**
     * <code>optional string date = 5;</code>
     */
    public java.lang.String getDate() {
      java.lang.Object ref = date_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        date_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string date = 5;</code>
     */
    public com.google.protobuf.ByteString
        getDateBytes() {
      java.lang.Object ref = date_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        date_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string date = 5;</code>
     */
    public Builder setDate(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      date_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string date = 5;</code>
     */
    public Builder clearDate() {

      date_ = getDefaultInstance().getDate();
      onChanged();
      return this;
    }
    /**
     * <code>optional string date = 5;</code>
     */
    public Builder setDateBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      date_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object policy_ = "";
    /**
     * <code>optional string policy = 6;</code>
     */
    public java.lang.String getPolicy() {
      java.lang.Object ref = policy_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        policy_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string policy = 6;</code>
     */
    public com.google.protobuf.ByteString
        getPolicyBytes() {
      java.lang.Object ref = policy_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        policy_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string policy = 6;</code>
     */
    public Builder setPolicy(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      policy_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string policy = 6;</code>
     */
    public Builder clearPolicy() {

      policy_ = getDefaultInstance().getPolicy();
      onChanged();
      return this;
    }
    /**
     * <code>optional string policy = 6;</code>
     */
    public Builder setPolicyBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      policy_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object signature_ = "";
    /**
     * <code>optional string signature = 7;</code>
     */
    public java.lang.String getSignature() {
      java.lang.Object ref = signature_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        signature_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string signature = 7;</code>
     */
    public com.google.protobuf.ByteString
        getSignatureBytes() {
      java.lang.Object ref = signature_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        signature_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string signature = 7;</code>
     */
    public Builder setSignature(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }

      signature_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string signature = 7;</code>
     */
    public Builder clearSignature() {

      signature_ = getDefaultInstance().getSignature();
      onChanged();
      return this;
    }
    /**
     * <code>optional string signature = 7;</code>
     */
    public Builder setSignatureBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);

      signature_ = value;
      onChanged();
      return this;
    }
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return this;
    }

    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return this;
    }


    // @@protoc_insertion_point(builder_scope:signal.AvatarUploadAttributes)
  }

  // @@protoc_insertion_point(class_scope:signal.AvatarUploadAttributes)
  private static final org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes();
  }

  public static org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<AvatarUploadAttributes>
      PARSER = new com.google.protobuf.AbstractParser<AvatarUploadAttributes>() {
    public AvatarUploadAttributes parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return new AvatarUploadAttributes(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<AvatarUploadAttributes> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<AvatarUploadAttributes> getParserForType() {
    return PARSER;
  }

  public org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}
