/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.sms;


import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SmsSender {

  private final TwilioSmsSender twilioSender;

  public SmsSender(TwilioSmsSender twilioSender) {
    this.twilioSender = twilioSender;
  }

  public void deliverSmsVerification(String destination, Optional<String> clientType, String verificationCode) {
    // Fix up mexico numbers to 'mobile' format just for SMS delivery.
    if (destination.startsWith("+52") && !destination.startsWith("+521")) {
      destination = "+521" + destination.substring("+52".length());
    }

    twilioSender.deliverSmsVerification(destination, clientType, verificationCode);
  }

  public void deliverVoxVerification(String destination, String verificationCode, List<LanguageRange> languageRanges) {
    twilioSender.deliverVoxVerification(destination, verificationCode, languageRanges);
  }

  public CompletableFuture<Optional<String>> deliverSmsVerificationWithTwilioVerify(String destination, Optional<String> clientType,
      String verificationCode, List<LanguageRange> languageRanges) {
    // Fix up mexico numbers to 'mobile' format just for SMS delivery.
    if (destination.startsWith("+52") && !destination.startsWith("+521")) {
      destination = "+521" + destination.substring(3);
    }

    return twilioSender.deliverSmsVerificationWithVerify(destination, clientType, verificationCode, languageRanges);
  }

  public CompletableFuture<Optional<String>> deliverVoxVerificationWithTwilioVerify(String destination, String verificationCode,
      List<LanguageRange> languageRanges) {

      return twilioSender.deliverVoxVerificationWithVerify(destination, verificationCode, languageRanges);
  }

  public void reportVerificationSucceeded(String verificationSid) {
    twilioSender.reportVerificationSucceeded(verificationSid);
  }
}
