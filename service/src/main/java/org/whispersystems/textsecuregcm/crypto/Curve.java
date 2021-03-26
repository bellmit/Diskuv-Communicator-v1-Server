package org.whispersystems.textsecuregcm.crypto;

import java.security.InvalidKeyException;

public class Curve {

  public  static final int DJB_TYPE   = 0x05;

  public static ECKeyPair generateKeyPair() {
    org.whispersystems.libsignal.ecc.ECKeyPair ecKeyPair = org.whispersystems.libsignal.ecc.Curve.generateKeyPair();
    ECPublicKey  publicKey  =        SignalClientBridge.convertECPublicKeyFromClientToServer(ecKeyPair.getPublicKey());
    ECPrivateKey privateKey =        SignalClientBridge.convertECPrivateKeyFromClientToServer(ecKeyPair.getPrivateKey());

    return new ECKeyPair(publicKey, privateKey);
  }

  public static ECPublicKey decodePoint(byte[] bytes, int offset)
      throws InvalidKeyException
  {
    try {
      org.whispersystems.libsignal.ecc.ECPublicKey publicKey = org.whispersystems.libsignal.ecc.Curve.decodePoint(bytes, offset);
      return SignalClientBridge.convertECPublicKeyFromClientToServer(publicKey);
    } catch (org.whispersystems.libsignal.InvalidKeyException e) {
      throw new InvalidKeyException(e);
    }
  }

  public static ECPrivateKey decodePrivatePoint(byte[] bytes) {
    org.whispersystems.libsignal.ecc.ECPrivateKey privateKey = org.whispersystems.libsignal.ecc.Curve.decodePrivatePoint(bytes);
    return SignalClientBridge.convertECPrivateKeyFromClientToServer(privateKey);
  }

  public static byte[] calculateSignature(ECPrivateKey signingKey, byte[] message)
      throws InvalidKeyException
  {
    try {
      return org.whispersystems.libsignal.ecc.Curve.calculateSignature(SignalClientBridge.convertECPrivateKeyFromServerToClient(signingKey), message);
    } catch (org.whispersystems.libsignal.InvalidKeyException e) {
      throw new InvalidKeyException(e);
    }
  }

  public static boolean verifySignature(ECPublicKey signingKey, byte[] message, byte[] signature)
      throws InvalidKeyException
  {
    try {
      return org.whispersystems.libsignal.ecc.Curve.verifySignature(SignalClientBridge.convertECPublicKeyFromServerToClient(signingKey), message, signature);
    } catch (org.whispersystems.libsignal.InvalidKeyException e) {
      throw new InvalidKeyException(e);
    }
  }
}
