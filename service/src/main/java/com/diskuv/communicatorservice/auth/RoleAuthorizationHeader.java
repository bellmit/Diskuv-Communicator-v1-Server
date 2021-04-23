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
import org.whispersystems.textsecuregcm.util.Util;

import java.io.IOException;

/**
 * Parse the role header which is separate and complementary to the JWT-based Authorization HTTP
 * header.
 *
 * <p>The HTTP header is expected to be {@value #ROLE_AUTHORIZATION_HEADER}. The format is:
 *
 * <pre>
 *     Basic Base64{USERNAME:PASSWORD}
 * </pre>
 *
 * <p>For {@literal @Auth} <tt>group</tt>, the 'username' part of the header is the hex encoded
 * group public key. The 'password' part of the header is the auth credential presentation.
 */
public class RoleAuthorizationHeader {
  public static final String ROLE_AUTHORIZATION_HEADER = "X-Diskuv-Role-Authorization";
  private final String user;
  private final String password;

  private RoleAuthorizationHeader(String user, String password) {
    this.user = user;
    this.password = password;
  }

  public static RoleAuthorizationHeader fromFullHeader(String header)
      throws InvalidAuthorizationHeaderException {
    try {
      if (header == null) {
        throw new InvalidAuthorizationHeaderException("Null header");
      }

      String[] headerParts = header.split(" ");

      if (headerParts.length < 2) {
        throw new InvalidAuthorizationHeaderException("Invalid authorization header: " + header);
      }

      if (!"Basic".equals(headerParts[0])) {
        throw new InvalidAuthorizationHeaderException(
            "Unsupported authorization method: " + headerParts[0]);
      }

      String concatenatedValues = new String(java.util.Base64.getDecoder().decode(headerParts[1]));

      if (Util.isEmpty(concatenatedValues)) {
        throw new InvalidAuthorizationHeaderException("Bad decoded value: " + concatenatedValues);
      }

      final int i = concatenatedValues.indexOf(':');
      if (i < 0) {
        throw new InvalidAuthorizationHeaderException(
            "Badly formatted credentials: " + concatenatedValues);
      }

      final String username = concatenatedValues.substring(0, i);
      final String password = concatenatedValues.substring(i + 1);
      return new RoleAuthorizationHeader(username, password);
    } catch (IllegalArgumentException ioe) {
      throw new InvalidAuthorizationHeaderException(ioe);
    }
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }
}
