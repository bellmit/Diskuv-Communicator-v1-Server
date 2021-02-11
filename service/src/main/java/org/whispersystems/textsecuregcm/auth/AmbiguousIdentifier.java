package org.whispersystems.textsecuregcm.auth;

import java.util.UUID;

public class AmbiguousIdentifier {

  private final UUID   uuid;
  private final String number;

  public AmbiguousIdentifier(String target) {
    if (target.startsWith("+")) {
      this.uuid   = null;
      this.number = target;
    } else {
      this.uuid   = UUID.fromString(target);
      this.number = null;
    }
  }

  public String toString() {
    return asString();
  }

  public String asString() {
    if (uuid != null) {
      return uuid.toString();
    }
    return number;
  }

  public UUID getUuid() {
    return uuid;
  }

  public String getNumber() {
    return number;
  }

  public boolean hasUuid() {
    return uuid != null;
  }

  public boolean hasNumber() {
    return number != null;
  }

  @Override
  public String toString() {
    return hasUuid() ? uuid.toString() : number;
  }
}
