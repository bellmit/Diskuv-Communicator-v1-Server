/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Positive;

public class GroupConfiguration {
  @JsonProperty
  @Positive
  private int maxGroupSize;

  @JsonProperty
  @Positive
  private int maxGroupTitleLengthBytes;

  @JsonProperty
  @NotEmpty
  private String externalServiceSecret;

  public int getMaxGroupSize() {
    return maxGroupSize;
  }

  @VisibleForTesting
  public void setMaxGroupSize(int maxGroupSize) {
    this.maxGroupSize = maxGroupSize;
  }

  public int getMaxGroupTitleLengthBytes() {
    return maxGroupTitleLengthBytes;
  }

  @VisibleForTesting
  public void setMaxGroupTitleLengthBytes(int maxGroupTitleLengthBytes) {
    this.maxGroupTitleLengthBytes = maxGroupTitleLengthBytes;
  }

  public byte[] getExternalServiceSecret() throws DecoderException {
    return Hex.decodeHex(externalServiceSecret);
  }

  // [Diskuv Change] Add setter for programmatic config
  public void setExternalServiceSecret(String externalServiceSecret) {
    this.externalServiceSecret = externalServiceSecret;
  }
}
