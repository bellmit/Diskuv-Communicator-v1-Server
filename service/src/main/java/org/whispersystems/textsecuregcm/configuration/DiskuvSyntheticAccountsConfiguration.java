package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.whispersystems.textsecuregcm.util.ByteArrayAdapter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class DiskuvSyntheticAccountsConfiguration {

  /**
   * The salt for generating synthetic accounts.
   *
   * Since part of a SHA-256 hash, we need at least 256 bits (32 bytes) of random material.
   * And since part of HMAC-DRBG (HmacDrbg.java) with a nonce (currently unused), we need 3/2
   * more random material. So use 48 bytes of random material. The encoding is base64 without
   * any padding (48 bytes is divisible by the Base64 magic number 3, so no need to remove
   * padding if you use 48 bytes).
   *
   * Example: <pre>openssl rand -base64 48</pre>
   */
  @JsonProperty(required = true)
  @JsonSerialize(using = ByteArrayAdapter.Serializing.class)
  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
  @NotNull
  private byte[] sharedEntropyInput;

  @JsonIgnore
  public byte[] getSharedEntropyInput() {
    return sharedEntropyInput;
  }

  public void setSharedEntropyInput(byte[] sharedEntropyInput) {
    this.sharedEntropyInput = sharedEntropyInput;
  }
}
