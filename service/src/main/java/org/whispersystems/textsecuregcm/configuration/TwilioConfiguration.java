/*
  Copyright (C) 2013 Open WhisperSystems

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.configuration;

import com.google.common.annotations.VisibleForTesting;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class TwilioConfiguration {

  @NotEmpty
  private String accountId;

  @NotEmpty
  private String accountToken;

  @NotEmpty
  private String localDomain;

  @NotEmpty
  private String messagingServiceSid;

  @NotEmpty
  private String nanpaMessagingServiceSid;

  @NotEmpty
  private String verifyServiceSid;

  @NotNull
  @Valid
  private CircuitBreakerConfiguration circuitBreaker = new CircuitBreakerConfiguration();

  @NotNull
  @Valid
  private RetryConfiguration retry = new RetryConfiguration();

  @NotEmpty
  private String iosVerificationText;

  @NotEmpty
  private String androidNgVerificationText;

  @NotEmpty
  private String android202001VerificationText;

  @NotEmpty
  private String android202103VerificationText;

  @NotEmpty
  private String genericVerificationText;

  @NotEmpty
  private String androidAppHash;

  public String getAccountId() {
    return accountId;
  }

  @VisibleForTesting
  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getAccountToken() {
    return accountToken;
  }

  @VisibleForTesting
  public void setAccountToken(String accountToken) {
    this.accountToken = accountToken;
  }
  public String getLocalDomain() {
    return localDomain;
  }

  @VisibleForTesting
  public void setLocalDomain(String localDomain) {
    this.localDomain = localDomain;
  }

  public String getMessagingServiceSid() {
    return messagingServiceSid;
  }

  @VisibleForTesting
  public void setMessagingServiceSid(String messagingServiceSid) {
    this.messagingServiceSid = messagingServiceSid;
  }

  public String getNanpaMessagingServiceSid() {
    return nanpaMessagingServiceSid;
  }

  @VisibleForTesting
  public void setNanpaMessagingServiceSid(String nanpaMessagingServiceSid) {
    this.nanpaMessagingServiceSid = nanpaMessagingServiceSid;
  }

  public String getVerifyServiceSid() {
    return verifyServiceSid;
  }

  @VisibleForTesting
  public void setVerifyServiceSid(String verifyServiceSid) {
    this.verifyServiceSid = verifyServiceSid;
  }

  public CircuitBreakerConfiguration getCircuitBreaker() {
    return circuitBreaker;
  }

  @VisibleForTesting
  public void setCircuitBreaker(CircuitBreakerConfiguration circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  public RetryConfiguration getRetry() {
    return retry;
  }

  @VisibleForTesting
  public void setRetry(RetryConfiguration retry) {
    this.retry = retry;
  }

  public String getIosVerificationText() {
    return iosVerificationText;
  }

  @VisibleForTesting
  public void setIosVerificationText(String iosVerificationText) {
    this.iosVerificationText = iosVerificationText;
  }

  public String getAndroidNgVerificationText() {
    return androidNgVerificationText;
  }

  @VisibleForTesting
  public void setAndroidNgVerificationText(String androidNgVerificationText) {
    this.androidNgVerificationText = androidNgVerificationText;
  }

  public String getAndroid202001VerificationText() {
    return android202001VerificationText;
  }

  @VisibleForTesting
  public void setAndroid202001VerificationText(String android202001VerificationText) {
    this.android202001VerificationText = android202001VerificationText;
  }

  public String getAndroid202103VerificationText() {
    return android202103VerificationText;
  }

  @VisibleForTesting
  public void setAndroid202103VerificationText(String android202103VerificationText) {
    this.android202103VerificationText = android202103VerificationText;
  }

  public String getGenericVerificationText() {
    return genericVerificationText;
  }

  @VisibleForTesting
  public void setGenericVerificationText(String genericVerificationText) {
    this.genericVerificationText = genericVerificationText;
  }

  public String getAndroidAppHash() {
    return androidAppHash;
  }

  public void setAndroidAppHash(String androidAppHash) {
    this.androidAppHash = androidAppHash;
  }
}
