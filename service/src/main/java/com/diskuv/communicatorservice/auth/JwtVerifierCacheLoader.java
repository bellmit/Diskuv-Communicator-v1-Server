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
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.cache.CacheLoader;

import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A cache loader for JWT verifiers.
 *
 * <p>Although this class has been coded to accept most JW key sets that use public/private
 * algorithms, only RSA algorithms are supported because {@link Jwk#getPublicKey()} only supports
 * RSA as of <tt>jwks-rsa-0.15.0</tt>. There may be a future possibility to support the EC
 * algorithms. Algorithms like HS256 may never be supported since JW key sets typically don't
 * distribute shared secrets.
 *
 * <p>The verifiers will verify the signature and a couple extra requirements from
 * https://github.com/awslabs/aws-support-tools/tree/master/Cognito/decode-verify-jwt:
 *
 * <ul>
 *   <li>The token is not expired
 *   <li>The audience ("aud") specified in the payload matches the app client ID created in the
 *       Amazon Cognito user pool.
 * </ul>
 */
class JwtVerifierCacheLoader extends CacheLoader<String, JWTVerifier> {
  /** Allow 5 minutes of clock skew. */
  private static final long LEEWAY_SECONDS = TimeUnit.MINUTES.toSeconds(5);

  private static final RSAPrivateKey NULL_RSA_PRIVATE_KEY_TO_VERIFY_ONLY = null;
  private static final ECPrivateKey NULL_EC_PRIVATE_KEY_TO_VERIFY_ONLY = null;

  private final JwkProvider provider;
  private final String[] appClientIds;

  JwtVerifierCacheLoader(JwkProvider provider, List<String> appClientIds) {
    this.provider = provider;
    this.appClientIds = appClientIds.toArray(new String[appClientIds.size()]);
  }

  @Override
  public JWTVerifier load(String kid) throws JwkException {
    Jwk jwk = provider.get(kid);
    if (jwk == null) {
      return null;
    }
    final Algorithm algorithm;
    PublicKey publicKey = jwk.getPublicKey();
    if ("RS256".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, NULL_RSA_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else if ("RS384".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.RSA384((RSAPublicKey) publicKey, NULL_RSA_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else if ("RS512".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.RSA512((RSAPublicKey) publicKey, NULL_RSA_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else if ("ES256K".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.ECDSA256K((ECPublicKey) publicKey, NULL_EC_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else if ("ES256".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.ECDSA256((ECPublicKey) publicKey, NULL_EC_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else if ("ES384".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.ECDSA384((ECPublicKey) publicKey, NULL_EC_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else if ("ES512".equals(jwk.getAlgorithm())) {
      algorithm = Algorithm.ECDSA512((ECPublicKey) publicKey, NULL_EC_PRIVATE_KEY_TO_VERIFY_ONLY);
    } else {
      throw new IllegalArgumentException(
          "The algorithm " + jwk.getAlgorithm() + " is not supported");
    }

    return JWT.require(algorithm)
        .acceptLeeway(LEEWAY_SECONDS)
        .withAnyOfAudience(appClientIds)
        .build();
  }
}
