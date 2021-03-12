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
package org.whispersystems.textsecuregcm.auth;


import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.util.Util;

import java.io.IOException;

/**
 * Similar to original Signal 'AuthorizationHeader', but treats as if the device header is
 * separate and complementary to the JWT-based Authorization HTTP header. So the account
 * identifier is redundant, although the device id is definitely needed.
 *
 * The HTTP header is expected to be {@value #DEVICE_AUTHORIZATION_HEADER}. The format is:
 *
 * <pre>
 *     Basic Base64{DEVICE_ID:DEVICE_PASSWORD}
 * </pre>
 */
public class DeviceAuthorizationHeader {
  public static final String        DEVICE_AUTHORIZATION_HEADER = "X-Diskuv-Device-Authorization";
  private final long                deviceId;
  private final String              password;

  private DeviceAuthorizationHeader(long deviceId, String password) {
    this.deviceId   = deviceId;
    this.password   = password;
  }

  public static DeviceAuthorizationHeader fromUserAndPassword(String user, String password) throws InvalidAuthorizationHeaderException {
    try {
      return new DeviceAuthorizationHeader(Long.parseLong(user), password);
    } catch (NumberFormatException nfe) {
      throw new InvalidAuthorizationHeaderException(nfe);
    }
  }

  public static DeviceAuthorizationHeader fromFullHeader(String header) throws InvalidAuthorizationHeaderException {
    try {
      if (header == null) {
        throw new InvalidAuthorizationHeaderException("Null header");
      }

      String[] headerParts = header.split(" ");

      if (headerParts == null || headerParts.length < 2) {
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

      if (credentialParts == null || credentialParts.length < 2) {
        throw new InvalidAuthorizationHeaderException("Badly formatted credentials: " + concatenatedValues);
      }

      return fromUserAndPassword(credentialParts[0], credentialParts[1]);
    } catch (IOException ioe) {
      throw new InvalidAuthorizationHeaderException(ioe);
    }
  }

  public long getDeviceId() {
    return deviceId;
  }

  public String getDevicePassword() {
    return password;
  }
}
