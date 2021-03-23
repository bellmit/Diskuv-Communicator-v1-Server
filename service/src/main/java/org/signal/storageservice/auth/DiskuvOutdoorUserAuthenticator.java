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

package org.signal.storageservice.auth;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.Optional;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;

public class DiskuvOutdoorUserAuthenticator implements Authenticator<String, User> {

  private final MetricRegistry metricRegistry =
      SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter invalidJwtTokenMeter =
      metricRegistry.meter(name(getClass(), "authentication", "invalidJwtToken"));

  private final JwtAuthentication jwtAuthentication;

  public DiskuvOutdoorUserAuthenticator(JwtAuthentication jwtAuthentication) {
    this.jwtAuthentication = jwtAuthentication;
  }

  @Override
  public Optional<User> authenticate(String bearerToken) throws AuthenticationException {
    final UUID accountId;
    try {
      String emailAddress = jwtAuthentication.verifyBearerTokenAndGetEmailAddress(bearerToken);
      accountId = DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress);

    } catch (IllegalArgumentException iae) {
      invalidJwtTokenMeter.mark();
      return Optional.empty();
    }
    return Optional.of(new User(accountId));
  }
}
