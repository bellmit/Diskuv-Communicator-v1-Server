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

@DynamoDbBean
public class HouseItem {
  static final TableSchema<HouseItem> HOUSE_TABLE_SCHEMA = TableSchema.fromClass(HouseItem.class);
  static final String ATTRIBUTE_GROUP_ID = "houseGroupId";
  static final String ATTRIBUTE_OPTIMISTIC_LOCK_VERSION = "lockVersion";
  static final String ATTRIBUTE_HOUSE_ENABLED = "houseEnabled";
  static final String ATTRIBUTE_SUPPORT_USER_ID = "supportUserId";

  private byte[]  houseGroupId;
  private Integer optimisticLockVersion;
  private boolean houseEnabled;
  private String supportContactId;

  @Override
  protected HouseItem clone() {
    HouseItem item = new HouseItem();
    item.setHouseGroupId(houseGroupId.clone());
    item.setOptimisticLockVersion(optimisticLockVersion);
    item.setHouseEnabled(houseEnabled);
    item.setSupportContactId(supportContactId);
    return item;
  }

  @DynamoDbPartitionKey
  @DynamoDbAttribute(ATTRIBUTE_GROUP_ID)
  public byte[] getHouseGroupId() {
    return this.houseGroupId;
  }

  public void setHouseGroupId(byte[] houseGroupId) {
    this.houseGroupId = houseGroupId;
  }

  @DynamoDbVersionAttribute
  @DynamoDbAttribute(ATTRIBUTE_OPTIMISTIC_LOCK_VERSION)
  public Integer getOptimisticLockVersion() {
    return optimisticLockVersion;
  }

  public void setOptimisticLockVersion(Integer version) {
    this.optimisticLockVersion = version;
  }

  @DynamoDbAttribute(ATTRIBUTE_HOUSE_ENABLED)
  public boolean isHouseEnabled() {
    return houseEnabled;
  }

  public void setHouseEnabled(boolean houseEnabled) {
    this.houseEnabled = houseEnabled;
  }

  /**
   * The user id who should be contacted for any support requests made by people outside of the
   * house.
   */
  @DynamoDbAttribute(ATTRIBUTE_SUPPORT_USER_ID)
  public String getSupportContactId() {
    return supportContactId;
  }

  public void setSupportContactId(String supportContactId) {
    this.supportContactId = supportContactId;
  }
}
