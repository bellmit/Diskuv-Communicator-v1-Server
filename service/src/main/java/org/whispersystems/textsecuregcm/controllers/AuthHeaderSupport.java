package org.whispersystems.textsecuregcm.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.BearerTokenAuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.InvalidAuthorizationHeaderException;
import org.whispersystems.textsecuregcm.auth.JwtAuthentication;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.UUID;

public final class AuthHeaderSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHeaderSupport.class);

    private AuthHeaderSupport() {
    }

    public static UUID getAndValidateAccountUuid(JwtAuthentication jwtAuthentication, String authorizationHeader) {
        final String bearerToken;
        try {
            bearerToken = BearerTokenAuthorizationHeader.fromFullHeader(authorizationHeader);
        } catch (InvalidAuthorizationHeaderException e) {
            LOGGER.info("Bad Authorization Header", e);
            throw new WebApplicationException(Response.status(401).build());
        }
        final String emailAddress;
        try {
            emailAddress = jwtAuthentication.verifyBearerTokenAndGetEmailAddress(bearerToken);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(Response.status(401).build());
        }
        return DiskuvUuidUtil.uuidForEmailAddress(emailAddress);
    }

}