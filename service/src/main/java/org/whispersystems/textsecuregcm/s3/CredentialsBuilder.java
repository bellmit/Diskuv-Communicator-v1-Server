package org.whispersystems.textsecuregcm.s3;

import com.amazonaws.auth.*;
import org.whispersystems.textsecuregcm.configuration.CdnConfiguration;

public final class CredentialsBuilder {
  private CredentialsBuilder() {
  }

  public static AWSCredentialsProvider build(CdnConfiguration cdnConfiguration) {
    if (cdnConfiguration.shouldUseDefaultCredentials()) {
      return DefaultAWSCredentialsProviderChain.getInstance();
    }
    AWSCredentials credentials =
        new BasicAWSCredentials(
            cdnConfiguration.getAccessKey(), cdnConfiguration.getAccessSecret());
    return new AWSStaticCredentialsProvider(credentials);
  }
}
