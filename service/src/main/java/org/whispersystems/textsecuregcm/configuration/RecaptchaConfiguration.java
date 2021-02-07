package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;

public class RecaptchaConfiguration {

  @JsonProperty
  @NotEmpty
  private String secret;

  public String getSecret() {
    return secret;
  }

}
