package org.whispersystems.textsecuregcm.crypto;

import org.whispersystems.libsignal.InvalidKeyException;

/**
 * Provides compatibility between Signal-Server's Curve API and the API of
 * signal-client-java-0.1.5.jar, so that the implementation bugs of Signal-Server's Curve can be
 * avoided.
 *
 * <h2>History</h2>
 *
 * The original Curve implementation in the 2020 Signal-Server code base generated a few signatures
 * that were not verifiable by signal-client-java-0.1.5.jar. So jonah@ abandoned the code.
 *
 * <p>The bugs were likely due to the Signal-Server 2020 code using Curve25519 from
 * <tt>org.whispersystems:curve25519-java:0.5.0</tt> which had not been updated since May 4, 2018.
 *
 * <p>Would get the following error <strong>for some but not all</strong> synthetic pre-keys:
 *
 * <pre>
 *        Caused by: org.whispersystems.libsignal.InvalidKeyException: invalid signature detected
 *         at org.signal.client.internal.Native.SessionBuilder_ProcessPreKeyBundle(Native Method)
 *         at org.whispersystems.libsignal.SessionBuilder.process(SessionBuilder.java:87)
 * </pre>
 */
public class SignalClientBridge {
  public static ECPublicKey convertECPublicKeyFromClientToServer(
      org.whispersystems.libsignal.ecc.ECPublicKey publicKey) {
    byte[] bytes = publicKey.getPublicKeyBytes();
    return new DjbECPublicKey(bytes);
  }

  public static ECPrivateKey convertECPrivateKeyFromClientToServer(
      org.whispersystems.libsignal.ecc.ECPrivateKey privateKey) {
    byte[] bytes = privateKey.serialize();
    return new DjbECPrivateKey(bytes);
  }

  public static org.whispersystems.libsignal.ecc.ECPrivateKey convertECPrivateKeyFromServerToClient(
      ECPrivateKey privateKey) {
    byte[] bytes = privateKey.serialize();
    return org.whispersystems.libsignal.ecc.Curve.decodePrivatePoint(bytes);
  }

  public static org.whispersystems.libsignal.ecc.ECPublicKey convertECPublicKeyFromServerToClient(
      ECPublicKey publicKey) throws InvalidKeyException {
    byte[] bytes = publicKey.serialize();
    return org.whispersystems.libsignal.ecc.Curve.decodePoint(bytes, 0);
  }
}
