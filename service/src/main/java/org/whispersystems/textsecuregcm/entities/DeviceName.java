package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

public class DeviceName {

  @JsonProperty
  @NotEmpty
  @Size(max = 300, message = "This field must be less than 300 characters")
  private String deviceName;

  public DeviceName() {}

  public String getDeviceName() {
    return deviceName;
  }
}
