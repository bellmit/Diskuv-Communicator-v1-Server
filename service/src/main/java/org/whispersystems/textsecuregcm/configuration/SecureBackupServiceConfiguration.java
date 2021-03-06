package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public class SecureBackupServiceConfiguration {

  @NotEmpty
  @JsonProperty
  private String userAuthenticationTokenSharedSecret;

  @NotBlank
  @JsonProperty
  private String uri;

  @NotBlank
  @JsonProperty
  private String backupCaCertificate;

  @NotNull
  @Valid
  @JsonProperty
  private CircuitBreakerConfiguration circuitBreaker = new CircuitBreakerConfiguration();

  @NotNull
  @Valid
  @JsonProperty
  private RetryConfiguration retry = new RetryConfiguration();

  public byte[] getUserAuthenticationTokenSharedSecret() throws DecoderException {
    return Hex.decodeHex(userAuthenticationTokenSharedSecret.toCharArray());
  }

  @VisibleForTesting
  public void setUri(final String uri) {
    this.uri = uri;
  }

  public String getUri() {
    return uri;
  }

  @VisibleForTesting
  public void setBackupCaCertificate(final String backupCaCertificate) {
    this.backupCaCertificate = backupCaCertificate;
  }

  public String getBackupCaCertificate() {
    return backupCaCertificate;
  }

  public CircuitBreakerConfiguration getCircuitBreakerConfiguration() {
    return circuitBreaker;
  }

  public RetryConfiguration getRetryConfiguration() {
    return retry;
  }
}
