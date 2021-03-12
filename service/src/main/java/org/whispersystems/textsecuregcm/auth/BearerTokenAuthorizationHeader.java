/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Affero General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.auth;

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
