package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotEmpty;

public class AppConfigConfiguration {

  @JsonProperty
  private boolean skipAppConfig = false;

  @JsonProperty
  @NotEmpty
  private String application;

  @JsonProperty
  @NotEmpty
  private String environment;

  @JsonProperty
  @NotEmpty
  private String configuration;

  public boolean isSkipAppConfig() {
    return skipAppConfig;
  }

  public String getApplication() {
    return application;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getConfigurationName() {
    return configuration;
  }
}
