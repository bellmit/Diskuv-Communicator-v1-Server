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

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * A somewhat compatible analog to Signal's BigTable-backed GroupsTable.
 *
 * <p>Use this if you want something cheaper that is backed by DynamoDB. It is only cheaper
 * initially; if the table grows very large, then BigTable or DocumentDB will be more cost
 * effective.
 *
 * @author Jonah Beckford
 */
@DynamoDbBean
public class GroupItem {
  static final TableSchema<GroupItem> GROUPS_TABLE_SCHEMA = TableSchema.fromClass(GroupItem.class);
  static final String ATTRIBUTE_GROUP_ID = "groupId";
  static final String ATTRIBUTE_OPTIMISTIC_LOCK_VERSION = "lockVersion";
  static final String ATTRIBUTE_GROUP_VERSION = "groupVersion";
  static final String ATTRIBUTE_GROUP_BYTES = "groupBytes";
  static final String ATTRIBUTE_GROUP_BYTES_CHECKSUM = "groupBytesChecksum";

  private byte[] groupId;
  private Integer optimisticLockVersion;
  private int groupVersion;
  private byte[] groupBytes;
  private byte[] groupChecksum;

  @DynamoDbPartitionKey
  @DynamoDbAttribute(ATTRIBUTE_GROUP_ID)
  public byte[] getGroupId() {
    return this.groupId;
  }

  public void setGroupId(byte[] groupId) {
    this.groupId = groupId;
  }

  /**
   * Get the item version used for optimistic locking, which is how many times the item has been
   * updated within DynamoDB. It is _different_ from the group version.
   *
   * @return
   */
  @DynamoDbVersionAttribute
  @DynamoDbAttribute(ATTRIBUTE_OPTIMISTIC_LOCK_VERSION)
  public Integer getOptimisticLockVersion() {
    return optimisticLockVersion;
  }

  public void setOptimisticLockVersion(Integer version) {
    this.optimisticLockVersion = version;
  }

  @DynamoDbAttribute(ATTRIBUTE_GROUP_VERSION)
  public int getGroupVersion() {
    return groupVersion;
  }

  public void setGroupVersion(int groupVersion) {
    this.groupVersion = groupVersion;
  }

  @DynamoDbAttribute(ATTRIBUTE_GROUP_BYTES)
  public byte[] getGroupBytes() {
    return groupBytes;
  }

  public void setGroupBytes(byte[] groupBytes) {
    this.groupBytes = groupBytes;
  }

  @DynamoDbAttribute(ATTRIBUTE_GROUP_BYTES_CHECKSUM)
  public byte[] getGroupBytesChecksum() {
    return groupChecksum;
  }

  public void setGroupBytesChecksum(byte[] groupChecksum) {
    this.groupChecksum = groupChecksum;
  }
}
