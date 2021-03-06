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
package com.diskuv.communicatorservice.storage.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;

public class DiskuvGroupsConfiguration implements DiskuvDynamoDBConfiguration {
  public static final int CHECKSUM_SHARED_KEY_SIZE = 16;

  @JsonProperty(required = true)
  private String region;

  @JsonProperty private String groupsTableName = "Groups";

  @JsonProperty private String groupLogTableName = "GroupLog";

  @JsonProperty private String sanctuariesTableName = "Sanctuaries";

  @Override
  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getGroupsTableName() {
    return groupsTableName;
  }

  public String getGroupLogTableName() {
    return groupLogTableName;
  }

  public String getSanctuariesTableName() {
    return sanctuariesTableName;
  }

  @JsonProperty(required = true)
  private String checksumSharedKey;

  public byte[] getChecksumSharedKey() {
    return BaseEncoding.base64().decode(checksumSharedKey);
  }

  public void setChecksumSharedKey(byte[] checksumSharedKey) {
    Preconditions.checkArgument(
        checksumSharedKey != null && checksumSharedKey.length == CHECKSUM_SHARED_KEY_SIZE);
    this.checksumSharedKey = BaseEncoding.base64().encode(checksumSharedKey);
  }

  private int numberOfGroupCacheCheckingThreads = 10;

  @JsonProperty
  public int getNumberOfGroupCacheCheckingThreads() {
    return numberOfGroupCacheCheckingThreads;
  }

  public void setNumberOfGroupCacheCheckingThreads(int numberOfGroupCacheCheckingThreads) {
    this.numberOfGroupCacheCheckingThreads = numberOfGroupCacheCheckingThreads;
  }

  @JsonProperty @Nonnull
  private List<String> emailAddressesAllowedToDeploySanctuary = new LinkedList<>();

  @Nonnull
  public List<String> getEmailAddressesAllowedToDeploySanctuary() {
    return ImmutableList.copyOf(emailAddressesAllowedToDeploySanctuary);
  }

  public void setEmailAddressesAllowedToDeploySanctuary(
      @Nonnull List<String> emailAddressesAllowedToDeploySanctuary) {
    this.emailAddressesAllowedToDeploySanctuary = emailAddressesAllowedToDeploySanctuary;
  }

  @JsonProperty(required = true)
  private DiskuvAwsCredentialsType credentialsType;

  @JsonProperty private String endpointOverride;

  @JsonProperty private String accessKey;

  @JsonProperty private String secretKey;

  @Override
  public DiskuvAwsCredentialsType getCredentialsType() {
    return credentialsType;
  }

  @Override
  public void setCredentialsType(DiskuvAwsCredentialsType credentialsType) {
    this.credentialsType = credentialsType;
  }

  @Override
  public String getEndpointOverride() {
    return endpointOverride;
  }

  @Override
  public String getAccessKey() {
    return accessKey;
  }

  @Override
  public String getSecretKey() {
    return secretKey;
  }
}
