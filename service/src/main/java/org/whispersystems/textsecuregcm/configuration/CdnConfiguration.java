package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class CdnConfiguration {
  @JsonProperty
  private String accessKey;

  @JsonProperty
  private String accessSecret;

  @NotEmpty
  @JsonProperty
  private String bucket;

  @NotEmpty
  @JsonProperty
  private String region;

  public String getAccessKey() {
    return accessKey;
  }

  public String getAccessSecret() {
    return accessSecret;
  }

  /**
   * If this method returns true, then the caller must access to AWS credentials through
   * <a href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html">AWS SDK v2: Use the default credential provider chain</a>
   * or
   * <a href="https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html">AWS SDK v1: Using the Default Credential Provider Chain</a>
   *
   * @return true if and only if there is both a non-empty access key and a non-empty access secret
   */
  public boolean shouldUseDefaultCredentials() {
    return accessKey == null || accessKey.isEmpty() || accessSecret == null || accessSecret.isEmpty();
  }

  public String getBucket() {
    return bucket;
  }

  public String getRegion() {
    return region;
  }

}
