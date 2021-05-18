package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public class PaymentsServiceConfiguration {

  @JsonProperty
  private boolean enabled = true;

  @NotEmpty
  @JsonProperty
  private String userAuthenticationTokenSharedSecret;

  @NotEmpty
  @JsonProperty
  private String fixerApiKey;

  @NotEmpty
  @JsonProperty
  private List<String> paymentCurrencies;

  public byte[] getUserAuthenticationTokenSharedSecret() throws DecoderException {
    return Hex.decodeHex(userAuthenticationTokenSharedSecret.toCharArray());
  }

  public String getFixerApiKey() {
    return fixerApiKey;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public List<String> getPaymentCurrencies() {
    return paymentCurrencies;
  }
}
