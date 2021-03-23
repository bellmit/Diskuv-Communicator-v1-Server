package com.diskuv.communicatorservice.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class HouseAttributes {
  @JsonProperty(required = true)
  private UUID supportContactId;

  @JsonProperty(required = true)
  private boolean houseEnabled;

  public HouseAttributes() {}

  public HouseAttributes(UUID supportContactId) {
    this.supportContactId = supportContactId;
  }

  public UUID getSupportContactId() {
    return supportContactId;
  }

  public boolean isHouseEnabled() {
    return houseEnabled;
  }
}
