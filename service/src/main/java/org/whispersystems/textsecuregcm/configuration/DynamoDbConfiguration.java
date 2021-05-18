/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.configuration;

import com.diskuv.communicatorservice.storage.configuration.DiskuvAwsCredentialsType;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.time.Duration;

public class DynamoDbConfiguration implements com.diskuv.communicatorservice.storage.configuration.DiskuvDynamoDBConfiguration {

    private com.diskuv.communicatorservice.storage.configuration.DiskuvAwsCredentialsType credentialsType;
    private String endpointOverride;
    private String accessKey;
    private String secretKey;

    @Override
    @JsonProperty(defaultValue = "INSTANCE_PROFILE")
    public com.diskuv.communicatorservice.storage.configuration.DiskuvAwsCredentialsType getCredentialsType() {
      return credentialsType;
    }

    @Override
    public void setCredentialsType(final DiskuvAwsCredentialsType credentialsType) {
      this.credentialsType = credentialsType;
    }

    @Override
    @JsonProperty
    public String getEndpointOverride() {
      return endpointOverride;
    }

    @Override
    @JsonProperty
    public String getAccessKey() {
      return accessKey;
    }

    @Override
    @JsonProperty
    public String getSecretKey() {
      return secretKey;
    }

    private String   region;
    private String   tableName;
    private Duration clientExecutionTimeout = Duration.ofSeconds(30);
    private Duration clientRequestTimeout   = Duration.ofSeconds(10);

    @Override
    @Valid
    @NotEmpty
    @JsonProperty
    public String getRegion() {
        return region;
    }

    @Valid
    @NotEmpty
    @JsonProperty
    public String getTableName() {
        return tableName;
    }

    @JsonProperty
    public Duration getClientExecutionTimeout() {
        return clientExecutionTimeout;
    }

    @JsonProperty
    public Duration getClientRequestTimeout() {
        return clientRequestTimeout;
    }
}
