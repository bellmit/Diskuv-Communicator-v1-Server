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
package com.diskuv.communicator.configurator;

import com.google.common.base.Preconditions;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Optional;

public class PemUtils {
  public static final class PublicPrivateKeyPair {
    @Nonnull private final ECPublicKey publicKey;
    @Nullable private final ECPrivateKey privateKey;

    PublicPrivateKeyPair(ECPublicKey publicKey, Optional<ECPrivateKey> privateKey) {
      this.publicKey = publicKey;
      this.privateKey = privateKey.orElse(null);
    }

    @Nonnull
    public ECPublicKey getPublicKey() {
      return publicKey;
    }

    @Nullable
    public ECPrivateKey getPrivateKey() {
      return privateKey;
    }
  }

  public static PublicPrivateKeyPair getKeyPair(PemReader reader) throws IOException, InvalidKeyException {
    ECPublicKey publicKey = null;
    ECPrivateKey privateKey = null;

    PemObject o;
    o = reader.readPemObject();
    if ("PUBLIC KEY".equals(o.getType())) {
      publicKey = Curve.decodePoint(o.getContent(), 0);
    } else if ("PRIVATE KEY".equals(o.getType())) {
      privateKey = Curve.decodePrivatePoint(o.getContent());
    }

    o = reader.readPemObject();
    if ("PUBLIC KEY".equals(o.getType())) {
      Preconditions.checkArgument(publicKey == null, "There were two public keys in 'reader'");
      publicKey = Curve.decodePoint(o.getContent(), 0);
    } else if ("PRIVATE KEY".equals(o.getType())) {
      Preconditions.checkArgument(privateKey == null, "There were two private keys in 'reader'");
      privateKey = Curve.decodePrivatePoint(o.getContent());
    }

    Preconditions.checkArgument(publicKey != null, "There was no public key in 'reader'");
    return new PublicPrivateKeyPair(publicKey, Optional.ofNullable(privateKey));
  }
}
