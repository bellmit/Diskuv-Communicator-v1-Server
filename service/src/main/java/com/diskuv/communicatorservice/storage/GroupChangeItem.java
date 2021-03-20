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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * A somewhat compatible analog to Signal's BigTable-backed GroupLogTable.
 *
 * <p>Use this if you want something cheaper that is backed by DynamoDB. It is only cheaper
 * initially; if the table grows very large, then BigTable or DocumentDB will be more cost
 * effective.
 *
 * <p>This is an <strong>unbalanced</strong> table. The hot accesses will be for records that are
 * part of groups with many members (meaning frequent reads), and which have high version numbers
 * (clients read from where they stopped last). One huge mitigation is to make use of a good cache
 * to reduce the hot reads; these change items are immutable so they are trivial to cache. {@link
 * GroupLogDao} will manage the cache automatically as long as you supply a {@link GroupChangeCache}
 * implementation.
 *
 * @author Jonah Beckford
 */
@DynamoDbBean
public class GroupChangeItem {
  static final TableSchema<GroupChangeItem> GROUP_LOG_TABLE_SCHEMA =
      TableSchema.fromClass(GroupChangeItem.class);
  static final String ATTRIBUTE_GROUP_ID = "groupId";
  static final String ATTRIBUTE_GROUP_VERSION = "groupVersion";
  static final String ATTRIBUTE_GROUP_BYTES = "groupBytes";
  static final String ATTRIBUTE_GROUP_CHANGE_BYTES = "groupChangeBytes";

  private byte[] groupId;
  private int groupVersion;
  private byte[] groupBytes;
  private byte[] groupChangeBytes;

  @DynamoDbPartitionKey
  @DynamoDbAttribute(ATTRIBUTE_GROUP_ID)
  public byte[] getGroupId() {
    return this.groupId;
  }

  public void setGroupId(byte[] groupId) {
    this.groupId = groupId;
  }

  @DynamoDbSortKey
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

  @DynamoDbAttribute(ATTRIBUTE_GROUP_CHANGE_BYTES)
  public byte[] getGroupChangeBytes() {
    return groupChangeBytes;
  }

  public void setGroupChangeBytes(byte[] groupChangeBytes) {
    this.groupChangeBytes = groupChangeBytes;
  }
}
