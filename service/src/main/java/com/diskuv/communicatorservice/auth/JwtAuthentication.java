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

import com.auth0.jwk.GuavaCachedJwkProvider;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import org.whispersystems.textsecuregcm.configuration.JwtKeysConfiguration;

import javax.annotation.Nonnull;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class JwtAuthentication {
  /**
   * Typically there are only 1 or 2 keys in a key set. One is active, and the other is there in
   * case of fail-over.
   */
  private static final long MAX_CACHED_KEY_SET_ENTRIES = 5;

  private final LoadingCache<String, JWTVerifier> jwtVerifierLoadingCache;

  public JwtAuthentication(JwtKeysConfiguration jwtKeys) {
    // With jitter we refresh cache every 30-60 minutes.
    int jitter = new SecureRandom().nextInt(30);
    int expiresInMinutes = 30 + jitter;

    // We cache the URL download of the key set
    UrlJwkProvider http = new UrlJwkProvider(jwtKeys.getDomain());
    GuavaCachedJwkProvider provider =
        new GuavaCachedJwkProvider(
            http, MAX_CACHED_KEY_SET_ENTRIES, expiresInMinutes, TimeUnit.MINUTES);

    // But we also cache the Verifier since it is re-usable
    this.jwtVerifierLoadingCache =
        CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHED_KEY_SET_ENTRIES)
            .expireAfterWrite(Duration.ofMinutes(expiresInMinutes))
            .build(new JwtVerifierCacheLoader(provider, jwtKeys.getAppClientIds()));

    // Load the cache right now (early detection of problems!)
    try {
      for (Jwk jwk : http.getAll()) {
        jwtVerifierLoadingCache.get(jwk.getId());
      }
    } catch (SigningKeyNotFoundException | ExecutionException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Follows the Cognito instructions for verifying a JWT token, and make sures that Cognito claims
   * the email has been validated, and finally gives back the email address.
   *
   * <p>Specifically from
   * https://github.com/awslabs/aws-support-tools/tree/master/Cognito/decode-verify-jwt:
   *
   * <p>To verify the signature of an Amazon Cognito JWT, search for the key with a key ID that
   * matches the key ID of the JWT, then use libraries to decode the token and verify the signature.
   * Be sure to also verify that:
   *
   * <p>* The token is not expired.
   *
   * <p>* The audience ("aud") specified in the payload matches the app client ID created in the
   * Amazon Cognito user pool.
   */
  public @Nonnull String verifyBearerTokenAndGetEmailAddress(String bearerToken) throws IllegalArgumentException {
    // 1. search for the key with a key ID that matches the key ID of the JWT
    DecodedJWT unverifiedJwt;
    try {
      unverifiedJwt = JWT.decode(bearerToken);
    } catch (JWTDecodeException e) {
      throw new IllegalArgumentException(e);
    }
    String keyId = unverifiedJwt.getKeyId();
    JWTVerifier jwtVerifier;
    try {
      jwtVerifier = jwtVerifierLoadingCache.get(keyId);
    } catch (ExecutionException e) {
      throw new IllegalArgumentException(e);
    }

    // 2. verify the signature
    // The following requirements are already part of JWTVerifier through JwtVerifierCacheLoader:
    // * check token is not expired
    // * check audience matches app client ID
    DecodedJWT jwt;
    try {
      jwt = jwtVerifier.verify(bearerToken);
    } catch (JWTVerificationException e) {
      throw new IllegalArgumentException(e);
    }

    // Make sure email address was verified by Cognito
    String email = jwt.getClaim("email").asString();
    Preconditions.checkArgument(email != null, "No email claim was given by Cognito");
    Preconditions.checkArgument(!email.isEmpty(), "An empty email claim was given by Cognito");
    Preconditions.checkArgument(
        Boolean.TRUE.equals(jwt.getClaim("email_verified").asBoolean()),
        "The email address was not claimed by Cognito as a true 'email_verified'");
    return email;
  }
}
