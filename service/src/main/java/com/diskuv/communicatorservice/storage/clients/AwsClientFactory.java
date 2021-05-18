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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.diskuv.communicatorservice.storage.configuration.DiskuvDynamoDBConfiguration;
import com.google.common.base.Preconditions;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;

public class AwsClientFactory {
  private final DiskuvDynamoDBConfiguration configuration;

  public AwsClientFactory(DiskuvDynamoDBConfiguration configuration) {
    this.configuration = configuration;
  }

  public AwsCredentialsProvider getAwsCredentialsProvider() {
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
      case INSTANCE_PROFILE:
        return InstanceProfileCredentialsProvider.create();
      default:
        throw new IllegalStateException(
            "No handling for credentials type: " + configuration.getCredentialsType());
    }
  }

  public AWSCredentialsProvider getAWSCredentialsProvider() {
    switch (configuration.getCredentialsType()) {
      case BASIC:
        Preconditions.checkArgument(
            configuration.getAccessKey() != null,
            "Must supply an accessKey when using BASIC credentials");
        Preconditions.checkArgument(
            configuration.getSecretKey() != null,
            "Must supply an secretKey when using BASIC credentials");
        return new AWSStaticCredentialsProvider(new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey()));
      case DEFAULT:
        return DefaultAWSCredentialsProviderChain.getInstance();
      case INSTANCE_PROFILE:
        return com.amazonaws.auth.InstanceProfileCredentialsProvider.getInstance();
      default:
        throw new IllegalStateException(
            "No handling for credentials type: " + configuration.getCredentialsType());
    }
  }

  public AmazonDynamoDBClientBuilder getAmazonDynamoDBClientBuilder() {
    final AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard()
        .withCredentials(getAWSCredentialsProvider());
    // For com.amazonaws.client.builder.AwsClientBuilder:
    // > Only one of Region or EndpointConfiguration may be set.
    if (configuration.getEndpointOverride() != null) {
      builder.setEndpointConfiguration(new EndpointConfiguration(configuration.getEndpointOverride(),
          configuration.getRegion()));
    } else {
      builder.setRegion(configuration.getRegion());
    }
    return builder;
  }

  public AmazonDynamoDBAsyncClientBuilder getAmazonDynamoDBAsyncClientBuilder() {
    final AmazonDynamoDBAsyncClientBuilder builder = AmazonDynamoDBAsyncClientBuilder.standard()
        .withCredentials(getAWSCredentialsProvider());
    // For com.amazonaws.client.builder.AwsClientBuilder:
    // > Only one of Region or EndpointConfiguration may be set.
    if (configuration.getEndpointOverride() != null) {
      builder.setEndpointConfiguration(new EndpointConfiguration(configuration.getEndpointOverride(),
          configuration.getRegion()));
    } else {
      builder.setRegion(configuration.getRegion());
    }
    return builder;
  }

  public DynamoDbAsyncClient getDynamoDbAsyncClient() {
    DynamoDbAsyncClientBuilder builder =
        DynamoDbAsyncClient.builder()
            .region(Region.of(configuration.getRegion()))
            .credentialsProvider(getAwsCredentialsProvider());
    if (configuration.getEndpointOverride() != null) {
      builder.endpointOverride(URI.create(configuration.getEndpointOverride()));
    }
    return builder.build();
  }
}
