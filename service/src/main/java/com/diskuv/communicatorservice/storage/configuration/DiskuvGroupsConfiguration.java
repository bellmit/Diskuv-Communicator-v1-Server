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
import com.google.common.io.BaseEncoding;

public class DiskuvGroupsConfiguration {
  public static final int CHECKSUM_SHARED_KEY_SIZE = 16;

  public enum CredentialsType {
    BASIC,
    DEFAULT
  }

  @JsonProperty(required = true)
  private String region;

  @JsonProperty private String groupsTableName = "Groups";

  @JsonProperty private String groupLogTableName = "GroupLog";

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

  @JsonProperty(required = true)
  private CredentialsType credentialsType;

  @JsonProperty private String endpointOverride;

  @JsonProperty private String accessKey;

  @JsonProperty private String secretKey;

  public CredentialsType getCredentialsType() {
    return credentialsType;
  }

  public void setCredentialsType(CredentialsType credentialsType) {
    this.credentialsType = credentialsType;
  }

  public String getEndpointOverride() {
    return endpointOverride;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }
}
