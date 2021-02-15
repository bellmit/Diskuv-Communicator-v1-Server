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
