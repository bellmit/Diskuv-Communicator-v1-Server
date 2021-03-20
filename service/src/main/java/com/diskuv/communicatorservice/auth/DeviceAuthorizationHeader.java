// Copyright 2021 Diskuv, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.diskuv.communicatorservice.auth;


import org.whispersystems.textsecuregcm.auth.InvalidAuthorizationHeaderException;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.util.Util;

import java.io.IOException;

/**
 * Parse the device header which is separate and complementary to the JWT-based Authorization HTTP
 * header. The account identifier is already part of the JWT token, and the device id is the remaining
 * part of the full identifier that is present in the device header.
 *
 * <p>The HTTP header is expected to be {@value #DEVICE_AUTHORIZATION_HEADER}. The format is:
 *
 * <pre>
 *     Basic Base64{DEVICE_ID:Base64{DEVICE_PASSWORD}}
 * </pre>
 */
public class DeviceAuthorizationHeader {
  public static final String        DEVICE_AUTHORIZATION_HEADER = "X-Diskuv-Device-Authorization";
  private final long                deviceId;
  private final byte[]              password;

  private DeviceAuthorizationHeader(long deviceId, byte[] password) {
    this.deviceId   = deviceId;
    this.password   = password;
  }

  public static DeviceAuthorizationHeader fromFullHeader(String header) throws InvalidAuthorizationHeaderException {
    try {
      if (header == null) {
        throw new InvalidAuthorizationHeaderException("Null header");
      }

      String[] headerParts = header.split(" ");

      if (headerParts.length < 2) {
        throw new InvalidAuthorizationHeaderException("Invalid authorization header: " + header);
      }

      if (!"Basic".equals(headerParts[0])) {
        throw new InvalidAuthorizationHeaderException("Unsupported authorization method: " + headerParts[0]);
      }

      String concatenatedValues = new String(Base64.decode(headerParts[1]));

      if (Util.isEmpty(concatenatedValues)) {
        throw new InvalidAuthorizationHeaderException("Bad decoded value: " + concatenatedValues);
      }

      String[] credentialParts = concatenatedValues.split(":");

      if (credentialParts.length < 2) {
        throw new InvalidAuthorizationHeaderException("Badly formatted credentials: " + concatenatedValues);
      }

      try {
        return new DeviceAuthorizationHeader(Long.parseLong(credentialParts[0]), Base64.decode(credentialParts[1]));
      } catch (NumberFormatException nfe) {
        throw new InvalidAuthorizationHeaderException(nfe);
      }
    } catch (IOException ioe) {
      throw new InvalidAuthorizationHeaderException(ioe);
    }
  }

  public long getDeviceId() {
    return deviceId;
  }

  public byte[] getDevicePassword() {
    return password;
  }
}
