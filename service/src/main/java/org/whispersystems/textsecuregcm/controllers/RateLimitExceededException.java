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
package org.whispersystems.textsecuregcm.controllers;

import java.time.Duration;

public class RateLimitExceededException extends Exception {

  private final Duration retryDuration;

  public RateLimitExceededException() {
    super();
    retryDuration = Duration.ZERO;
  }

  public RateLimitExceededException(String message) {
    super(message);
    retryDuration = Duration.ZERO;
  }

  public RateLimitExceededException(String message, long retryAfterMillis) {
    super(message);
    retryDuration = Duration.ofMillis(retryAfterMillis);
  }

  public Duration getRetryDuration() { return retryDuration; }
}
