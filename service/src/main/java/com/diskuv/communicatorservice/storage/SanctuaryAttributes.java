package com.diskuv.communicatorservice.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class SanctuaryAttributes {
  @JsonProperty(required = true)
  private UUID supportContactId;

  @JsonProperty(required = true)
  private boolean sanctuaryEnabled;

  public SanctuaryAttributes() {}

  public SanctuaryAttributes(UUID supportContactId) {
    this.supportContactId = supportContactId;
  }

  public SanctuaryAttributes(UUID supportContactId, boolean sanctuaryEnabled) {
    this.supportContactId = supportContactId;
    this.sanctuaryEnabled = sanctuaryEnabled;
  }

  public UUID getSupportContactId() {
    return supportContactId;
  }

  public boolean isSanctuaryEnabled() {
    return sanctuaryEnabled;
  }
}
