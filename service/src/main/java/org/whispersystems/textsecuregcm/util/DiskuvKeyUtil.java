package org.whispersystems.textsecuregcm.util;

import org.whispersystems.curve25519.JavaCurve25519Provider;
import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;

import java.security.InvalidKeyException;

public class DiskuvKeyUtil {
    public static ECPublicKey constructPublicKeyFromPrivateKey(ECPrivateKey privateKey) throws InvalidKeyException {
        JavaCurve25519Provider curve25519Provider = new JavaCurve25519Provider() {};
        byte[] publicKeyRaw = curve25519Provider.generatePublicKey(privateKey.serialize());
        byte[] curveType = {Curve.DJB_TYPE};
        byte[] publicKey = ByteUtil.combine(curveType, publicKeyRaw);
        ECPublicKey serverPublicKey = Curve.decodePoint(publicKey, 0);
        return serverPublicKey;
    }
}
