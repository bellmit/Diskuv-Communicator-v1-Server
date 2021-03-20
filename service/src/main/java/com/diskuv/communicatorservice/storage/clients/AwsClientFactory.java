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
package com.diskuv.communicatorservice.storage.clients;

import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import com.google.common.base.Preconditions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;

import java.net.URI;

public class AwsClientFactory {
  private final DiskuvGroupsConfiguration configuration;

  public AwsClientFactory(DiskuvGroupsConfiguration configuration) {
    this.configuration = configuration;
  }

  public AwsCredentialsProvider getCredentialsProvider() {
    switch (configuration.getCredentialsType()) {
      case BASIC:
        Preconditions.checkArgument(
            configuration.getAccessKey() != null,
            "Must supply an accessKey when using BASIC credentials");
        Preconditions.checkArgument(
            configuration.getSecretKey() != null,
            "Must supply an secretKey when using BASIC credentials");
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey()));
      case DEFAULT:
        return DefaultCredentialsProvider.create();
      default:
        throw new IllegalStateException(
            "No handling for credentials type: " + configuration.getCredentialsType());
    }
  }

  public DynamoDbAsyncClient getDynamoDbAsyncClient() {
    DynamoDbAsyncClientBuilder builder =
        DynamoDbAsyncClient.builder()
            .region(Region.of(configuration.getRegion()))
            .credentialsProvider(getCredentialsProvider());
    if (configuration.getEndpointOverride() != null) {
      builder.endpointOverride(URI.create(configuration.getEndpointOverride()));
    }
    return builder.build();
  }
}
