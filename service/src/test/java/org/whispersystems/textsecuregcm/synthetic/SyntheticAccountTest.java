package org.whispersystems.textsecuregcm.synthetic;

import com.google.protobuf.ByteString;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.whispersystems.textsecuregcm.synthetic.SyntheticProfileStateTestCommons.*;

public class SyntheticAccountTest {
  private static final UUID UUID2 = DiskuvUuidUtil.uuidForOutdoorEmailAddress("mistle-finger@equator.com");

  @Test
  public void testSignatureWithCurve25519() throws IOException {
    SyntheticAccount account =
            new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID1);
    Curve25519 curve25519 = Curve25519.getInstance(Curve25519.JAVA);
    PossiblySyntheticDevice device = account.getDevices().iterator().next();

    byte[]     identityPublicKeyWithType  = Base64.getDecoder().decode(account.getIdentityKey());
    assertThat(identityPublicKeyWithType[0]).isEqualTo((byte) org.whispersystems.textsecuregcm.crypto.Curve.DJB_TYPE);
    byte[] identityPublicKey = ByteString.copyFrom(identityPublicKeyWithType).substring(1).toByteArray();

    byte[]     signedPreKeyPublic = Base64.getDecoder().decode(device.getSignedPreKey().getPublicKey());

    byte[] signedPreKeySignature = Base64.getDecoder().decode(device.getSignedPreKey().getSignature());

    boolean valid = curve25519.verifySignature(identityPublicKey, signedPreKeyPublic, signedPreKeySignature);
    assertThat(valid).isTrue();
  }

  @Test
  public void testSignatureWithCurveFromSignalClientJava() throws IOException, InvalidKeyException {
    SyntheticAccount account =
            new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID1);
    PossiblySyntheticDevice device = account.getDevices().iterator().next();

    byte[]     identityPublicKeyWithType  = Base64.getDecoder().decode(account.getIdentityKey());
    assertThat(identityPublicKeyWithType[0]).isEqualTo((byte) org.whispersystems.libsignal.ecc.Curve.DJB_TYPE);
    // UNUSED:   byte[] identityPublicKey = ByteString.copyFrom(identityPublicKeyWithType).substring(1).toByteArray();

    byte[]     signedPreKeyPublic = Base64.getDecoder().decode(device.getSignedPreKey().getPublicKey());

    byte[] signedPreKeySignature = Base64.getDecoder().decode(device.getSignedPreKey().getSignature());

    ECPublicKey signingKey = Curve.decodePoint(identityPublicKeyWithType, 0);
    boolean     valid      = org.whispersystems.libsignal.ecc.Curve.verifySignature(signingKey, signedPreKeyPublic, signedPreKeySignature);
    assertThat(valid).isTrue();
  }

  @Test
  public void testStability1() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID1);
    assertThat(account.getProfileName()).isEqualTo(NAME1);
    assertThat(account.getProfileEmailAddress()).isEqualTo(EMAIL1);
    assertThat(account.getAvatar()).isEqualTo(AVATAR1);
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.isGroupsV2Supported()).isTrue();
    assertThat(account.isGv1MigrationSupported()).isTrue();
    assertThat(account.isUnrestrictedUnidentifiedAccess()).isFalse();
    assertThat(account.getIdentityKey()).isEqualTo("BQcixKf7KQvA5DZ4szVqiy9cP41U5MTboJFv0l51mL0T");

    assertThat(account.getUnidentifiedAccessKey()).isNotPresent();
    assertThat(account.getDevices()).hasSize(1);
    assertThat(account.getAuthenticatedDevice()).isEqualTo(account.getDevice(1L));
    assertThat(account.getDevices())
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getId()).isEqualTo(1L);
              softly.assertThat(device.getRegistrationId()).isEqualTo(16045);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(8482700L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BR9Pd+757dGPP1YQog6VmJuxaji2yhal8sEf3Gopk7oT");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .hasSameSizeAs( // The signature is NOT deterministic after a switch to signal-client-java's Curve, so this is not `isEqualTo`
                      "7WQTW3FdNQwwpLyqOYC5hvHt+XbX71oKhRhW+Y7ubdlxbjWesbl4CFToJE3l9h6nbhUP/liNsjklaJqAlvXogA");
              softly.assertAll();
            });
  }

  @Test
  public void testStability2() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID2);
    assertThat(account.getDevices()).hasSize(1);
    assertThat(account.getProfileName())
        .isEqualTo(
            "dWFTKZe4Sp8F9xMGihXt9DC3SExBPkfWdRH4ks3j90lhW41hC7V6uIwb4RELSbr0jEAOXiDJozUkpgyefZkWqE8w49c/AplLSEzOcoBn6oqp6UNBSC9BAbnsB0etKKSGH+dW7YZsaQDeOkmuzUsSxNf81yMHhseu2i39gFfbAkXyzOS/7/tebRRXKSEcE7gZJD1ymOHHITiu8T/RnvYIaxIBduaHTN6sxpjMx8tEI47fN0HqPcwMNPkho8uW7V/g1m0eVpfsbzAydG0HxCiYSpLX8G03sSb+4ypA89xexRyffzkxw+ghZrW6qp5VEdkrEjc8DsqjqHlQkN8npVNy79CVe58RHdBNilR1iP0ckN+C4I5FglX4bcSZoGFa");
    assertThat(account.getProfileEmailAddress())
        .isEqualTo(
            "vyWaMrB1TW+l96D9AEYKE4iyhPq7YZKMoO6XLWdZwHPiXpwB8zNOgjTa0aTDjmUbussfPV9Xzp6b2utVMA5M1Ep+tTogLv4+qSEXsxWkKKgteOj3+gHl1KWeU0GIfRV8KqFM8IRSoW6Yv0o0Fq5X2JV4gDnDUkINlYYAkJtzrG68fxkqMW/5xKdxDwHCZkFdSbMewwIkPPr1KMx1AJC/mWHHrR6an+1ScmexRcFt3Bsh67ufF4Ve31k4EZKHrR7OPbeGrOem6wIMfijBIfGSW0s2Xw64R0qfu2hmP1Fd3zMqXj6bzX2616cwQtxytv/nlzCymwI29pTlcJ49qBlS6OBhdlUfyCSS0Ootanb3AsGoBEMMMOi1qh92zVmvpv7dExhia4KNdhg8i4U49CXI1xpcZfOk0nyV6BEAzngGW6bFpG2JPnwM6cZHysjLhxWNIhqyW+3nMzGh9opn");
    assertThat(account.getAvatar()).isNull();
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.isGroupsV2Supported()).isTrue();
    assertThat(account.isGv1MigrationSupported()).isTrue();
    assertThat(account.isUnrestrictedUnidentifiedAccess()).isFalse();
    assertThat(account.getIdentityKey()).isEqualTo("BQt+VoFFW2txhdJ0PkVqGD/RALynEamUv6+sxhJ2m5Vz");

    assertThat(account.getUnidentifiedAccessKey()).isNotPresent();
    assertThat(account.getAuthenticatedDevice()).isEqualTo(account.getDevice(1L));
    assertThat(account.getDevices())
        .filteredOn("id", 1L)
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getRegistrationId()).isEqualTo(16176);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(4709618L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BUUttf2hmnCwYvqSecjED48dDuH+SSCqITcTiRUzN15y");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .hasSameSizeAs( // The signature is NOT deterministic after a switch to signal-client-java's Curve, so this is not `isEqualTo`
                                  "RdZw9G73A++m8s0e3ABk/0tR/u15PKru6NH6qfSM5jwePVJC3F4yu2nRf0UwEaEUEA5P3rE34kZrbTirooxvBw");
              softly.assertAll();
            });
  }
}
