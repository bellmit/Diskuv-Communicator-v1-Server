package org.whispersystems.textsecuregcm.auth;

import io.dropwizard.auth.AuthFilter;

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
 * org.whispersystems.textsecuregcm.auth.DeviceAuthorizationHeader#DEVICE_AUTHORIZATION_HEADER}
 * header.
 *
 * <p>Merges {@link io.dropwizard.auth.oauth.OAuthCredentialAuthFilter} for the
 * <tt>Authorization</tt> account header and the {@link
 * io.dropwizard.auth.basic.BasicCredentialAuthFilter} for the {@value
 * org.whispersystems.textsecuregcm.auth.DeviceAuthorizationHeader#DEVICE_AUTHORIZATION_HEADER}
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
public class DiskuvCredentialAuthFilter<P extends Principal>
    extends AuthFilter<DiskuvCredentials, P> {
    private DiskuvCredentialAuthFilter() {
    }

    public void filter(ContainerRequestContext requestContext) throws IOException {
        String accountAuthorizationHeader = requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String deviceAuthorizationHeader = requestContext.getHeaders().getFirst(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER);
        DiskuvCredentials credentials = this.getCredentials(accountAuthorizationHeader, deviceAuthorizationHeader);

        if (!this.authenticate(requestContext, credentials, SecurityContext.BASIC_AUTH)) {
            throw new WebApplicationException(this.unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    @Nullable
    private DiskuvCredentials getCredentials(String accountAuthorizationHeader, String deviceAuthorizationHeader) {
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
            return new DiskuvCredentials(bearerToken, header.getDeviceId(), header.getDevicePassword());
        } catch (InvalidAuthorizationHeaderException e) {
            return null;
        }
    }

    public static class Builder<P extends Principal> extends AuthFilter.AuthFilterBuilder<DiskuvCredentials, P, DiskuvCredentialAuthFilter<P>> {
        public Builder() {
        }

        protected DiskuvCredentialAuthFilter<P> newInstance() {
            return new DiskuvCredentialAuthFilter();
        }
    }
}
