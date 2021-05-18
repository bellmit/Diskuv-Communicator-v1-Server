package com.diskuv.communicatorservice.storage.configuration;

public interface DiskuvDynamoDBConfiguration {

  String getRegion();

  DiskuvAwsCredentialsType getCredentialsType();

  void setCredentialsType(DiskuvAwsCredentialsType credentialsType);

  String getEndpointOverride();

  String getAccessKey();

  String getSecretKey();
}
