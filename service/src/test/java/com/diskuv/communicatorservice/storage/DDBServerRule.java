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

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import org.apache.commons.cli.ParseException;
import org.junit.rules.ExternalResource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;

public class DDBServerRule extends ExternalResource {
  private static final String PORT = "8889";
  private final DynamoDBProxyServer server;
  private final DynamoDbAsyncClient client;

  public DDBServerRule() {
    System.setProperty("sqlite4java.library.path", "target/lib");
    try {
      this.server =
          ServerRunner.createServerFromCommandLineArgs(new String[] {"-inMemory", "-port", PORT});
    } catch (ParseException e) {
      throw new AssertionError("Could not create DynamoDB Local Server", e);
    }

    AwsCredentials awsCredentials =
        AwsBasicCredentials.create("someAccessKeyId", "someSecretAccessKey");
    this.client =
        DynamoDbAsyncClient.builder()
            .endpointOverride(URI.create("http://localhost:" + PORT))
            .region(Region.US_WEST_2)
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
            .build();
  }

  public DynamoDbAsyncClient client() {
    return client;
  }

  @Override
  protected void before() throws Throwable {
    server.start();
  }

  @Override
  protected void after() {
    try {
      server.stop();
    } catch (Exception e) {
      throw new AssertionError("DynamoDB Local Server could not stop", e);
    }
  }
}
