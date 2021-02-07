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
package org.whispersystems.textsecuregcm.configuration;


import com.fasterxml.jackson.annotation.JsonProperty;
import redis.clients.jedis.Protocol;

import java.time.Duration;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class RedisConfiguration {
  @JsonProperty
  private int readTimeoutMs = Protocol.DEFAULT_TIMEOUT;

  @JsonProperty
  @NotEmpty
  private String url;

  @JsonProperty
  @NotNull
  private List<String> replicaUrls;

  @JsonProperty
  @NotNull
  private Duration timeout = Duration.ofSeconds(10);

  @JsonProperty
  @NotNull
  @Valid
  private CircuitBreakerConfiguration circuitBreaker = new CircuitBreakerConfiguration();

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public String getUrl() {
    return url;
  }

  public List<String> getReplicaUrls() {
    return replicaUrls;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public CircuitBreakerConfiguration getCircuitBreakerConfiguration() {
    return circuitBreaker;
  }
}
