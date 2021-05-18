package com.diskuv.communicatorservice.storage.configuration;

public interface DiskuvDynamoDBConfiguration {

  String getRegion();

  DiskuvAwsCredentialsType getCredentialsType();

  String getEndpointOverride();

  String getAccessKey();

  String getSecretKey();
}
