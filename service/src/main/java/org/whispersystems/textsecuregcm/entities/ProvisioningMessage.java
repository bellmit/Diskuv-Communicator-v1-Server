package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;

public class ProvisioningMessage {

  @JsonProperty
  @NotEmpty
  private String body;

  public String getBody() {
    return body;
  }
}
