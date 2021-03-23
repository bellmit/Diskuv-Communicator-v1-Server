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

import io.dropwizard.auth.AuthFilter;
import org.whispersystems.textsecuregcm.auth.InvalidAuthorizationHeaderException;

import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;

/**
 * Authorizes both the account <tt>Authorization</tt> header and the device {@value
 * DeviceAuthorizationHeader#DEVICE_AUTHORIZATION_HEADER}
 * header.
 *
 * <p>Merges {@link io.dropwizard.auth.oauth.OAuthCredentialAuthFilter} for the
 * <tt>Authorization</tt> account header and the {@link
 * io.dropwizard.auth.basic.BasicCredentialAuthFilter} for the {@value
 * DeviceAuthorizationHeader#DEVICE_AUTHORIZATION_HEADER}
 * device header.
 *
 * <p>A full (fake!) example of the required HTTP headers is:
 *
 * <pre>
 *     Authorization: Bearer eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
 *     X-Diskuv-Device-Authorization: Basic MTpueGNycFdkdEpXYXR1YmRzbzUveUFYblgK
 * </pre>
 */
@Priority(1000)
public class DiskuvDeviceCredentialAuthFilter<P extends Principal>
    extends AuthFilter<DiskuvDeviceCredentials, P> {
    private DiskuvDeviceCredentialAuthFilter() {
    }

    public void filter(ContainerRequestContext requestContext) throws IOException {
        String accountAuthorizationHeader = requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String deviceAuthorizationHeader = requestContext.getHeaders().getFirst(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER);
        DiskuvDeviceCredentials credentials = this.getCredentials(accountAuthorizationHeader, deviceAuthorizationHeader);

        if (!this.authenticate(requestContext, credentials, SecurityContext.BASIC_AUTH)) {
            throw new WebApplicationException(this.unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    @Nullable
    private DiskuvDeviceCredentials getCredentials(String accountAuthorizationHeader, String deviceAuthorizationHeader) {
        if (accountAuthorizationHeader == null || deviceAuthorizationHeader == null) {
            return null;
        }

        // account
        final String bearerToken;
        try {
            bearerToken = BearerTokenAuthorizationHeader.fromFullHeader(accountAuthorizationHeader);
        } catch (InvalidAuthorizationHeaderException e) {
            return null;
        }

        // device
        try {
            DeviceAuthorizationHeader header = DeviceAuthorizationHeader.fromFullHeader(deviceAuthorizationHeader);
            return new DiskuvDeviceCredentials(bearerToken, header.getAccountId(), header.getDeviceId(), header.getDevicePassword());
        } catch (InvalidAuthorizationHeaderException e) {
            return null;
        }
    }

    public static class Builder<P extends Principal> extends AuthFilter.AuthFilterBuilder<DiskuvDeviceCredentials, P, DiskuvDeviceCredentialAuthFilter<P>> {
        public Builder() {
        }

        protected DiskuvDeviceCredentialAuthFilter<P> newInstance() {
            return new DiskuvDeviceCredentialAuthFilter();
        }
    }
}
