package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;

public class JwtKeysConfiguration {
  @JsonProperty(required = true) @NotNull private String domain;

  @JsonProperty(required = true) @NotNull private List<String> appClientIds = new LinkedList<>();

  @JsonIgnore
  public String getDomain() {
    return domain;
  }

  @JsonIgnore
  public List<String> getAppClientIds() {
    return appClientIds;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }
}
