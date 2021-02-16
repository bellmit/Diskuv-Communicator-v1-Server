package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;
import org.whispersystems.textsecuregcm.util.DiskuvKeyUtil;
import org.whispersystems.textsecuregcm.util.Pair;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DeterministicSampling {
  private final String salt;

  public DeterministicSampling(String salt) {
    this.salt = salt;
  }

  public Pair<ECPublicKey, ECPrivateKey> deterministicSampledKeyPair(String discriminator) {
    try {
      // make sha256(salt | discriminator)
      String actualStringToDigest = salt + discriminator;
      byte[] privateKeyBytes = new byte[32];
      MessageDigest digest256 = MessageDigest.getInstance("SHA-256");
      byte[] shaBytes = digest256.digest(actualStringToDigest.getBytes(StandardCharsets.UTF_8));
      System.arraycopy(shaBytes, 0, privateKeyBytes, 0, privateKeyBytes.length);

      // create private key
      ECPrivateKey privateKey = Curve.decodePrivatePoint(privateKeyBytes);

      // create valid public key
      ECPublicKey publicKey = DiskuvKeyUtil.constructPublicKeyFromPrivateKey(privateKey);

      return new Pair<>(publicKey, privateKey);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public int deterministicSampledIntInRange(
      String discriminator, int minInclusive, int maxExclusive) {
    // make sha256(salt | discriminator)
    String actualStringToDigest = salt + discriminator;
    MessageDigest digest256;
    try {
      digest256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
    byte[] digest =
        digest256.digest(actualStringToDigest.getBytes(StandardCharsets.UTF_8));
    ByteBuffer byteBuffer = ByteBuffer.wrap(digest);
    long modulus = 1 + maxExclusive - minInclusive;
    return (int) (minInclusive + (Math.abs(byteBuffer.getLong()) % modulus));
  }
}
