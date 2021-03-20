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

public final class BearerTokenAuthorizationHeader {

  public static final String AUTHORIZATION_PREFIX_FOR_BEARER = "Bearer";

  public static String fromFullHeader(String header) throws InvalidAuthorizationHeaderException {
    if (header == null) {
      throw new InvalidAuthorizationHeaderException("Null header");
    }

    String[] headerParts = header.split(" ");

    if (headerParts == null || headerParts.length != 2) {
      throw new InvalidAuthorizationHeaderException("Invalid authorization header: " + header);
    }

    if (!AUTHORIZATION_PREFIX_FOR_BEARER.equals(headerParts[0])) {
      throw new InvalidAuthorizationHeaderException(
          "Unsupported authorization method: " + headerParts[0]);
    }

    return headerParts[1];
  }
}
