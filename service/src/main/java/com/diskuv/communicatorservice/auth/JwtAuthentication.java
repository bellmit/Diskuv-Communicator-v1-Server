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

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.configuration.JwtKeysConfiguration;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;

public class JwtAuthentication {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Map<String, JWTVerifier> jwtVerifiers;

  public JwtAuthentication(JwtKeysConfiguration jwtKeysConfiguration) throws IOException, JwkException {
    ImmutableMap.Builder<String, JWTVerifier> jwtVerifierBuilder = ImmutableMap.builder();

    // Construct the url hash
    String sourceUrl  = jwtKeysConfiguration.getDomain() + "/.well-known/jwks.json";
    String urlHashDir = Hashing.sha256().hashString(sourceUrl, StandardCharsets.UTF_8).toString();

    // Find the jwks.json in the classpath
    String jwksJsonClasspathResource = "jwtKeys/" + urlHashDir + "/jwks.json";
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Enumeration<URL> jwksJsons = classLoader.getResources(jwksJsonClasspathResource);
    while (jwksJsons.hasMoreElements()) {
      URL jwksJsonUrl = jwksJsons.nextElement();
      log.info("Found JSON Web Key Set for {} at {}", jwtKeysConfiguration.getDomain(), jwksJsonUrl);

      // Grab all of the JWK material, keyed by the 'kid' identifier
      UrlJwkProvider localFileJwkProvider = new UrlJwkProvider(jwksJsonUrl);
      JwtVerifierCacheLoader verifierCacheLoader = new JwtVerifierCacheLoader(localFileJwkProvider, jwtKeysConfiguration.getAppClientIds());
      for (Jwk jwk : localFileJwkProvider.getAll()) {
        String kid = jwk.getId();
        jwtVerifierBuilder.put(kid, verifierCacheLoader.load(kid));
      }
    }

    jwtVerifiers = jwtVerifierBuilder.build();
    Preconditions.checkArgument(!jwtVerifiers.isEmpty(), "You have no JSON Web Key Sets, which were expected in the classpath at %s. Use configurator's generate-code-config and then check in the hashed files", jwksJsonClasspathResource);
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
    JWTVerifier jwtVerifier = jwtVerifiers.get(keyId);

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
