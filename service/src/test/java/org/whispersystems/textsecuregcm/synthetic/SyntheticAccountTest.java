package org.whispersystems.textsecuregcm.synthetic;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.whispersystems.textsecuregcm.synthetic.SyntheticProfileStateTestCommons.*;

public class SyntheticAccountTest {
  private static final UUID UUID2 = DiskuvUuidUtil.uuidForOutdoorEmailAddress("mistle-finger@equator.com");

  @Test
  public void testStability1() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID1);
    assertThat(account.getProfileName()).isEqualTo(NAME1);
    assertThat(account.getProfileEmailAddress()).isEqualTo(EMAIL1);
    assertThat(account.getAvatar()).isEqualTo(AVATAR1);
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.isGroupsV2Supported()).isFalse();
    assertThat(account.isUnrestrictedUnidentifiedAccess()).isFalse();
    assertThat(account.getIdentityKey()).isEqualTo("BbbkXgRclvWMPrhGBD05L7yTy30oMrJ0TRBlGlVxVMY7");

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
                  .isEqualTo("BQv4wsQba0lqiLhfwBndAoq8jj0gohiWEZEaYMDNj6MP");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "/rksEnntgQibQ3FdH3LcHyxHdmuCU3p82e+xKGDbu8F7l4UxGy/j2MXmSjA4AdodFC5xMziphb7Sse0wh41Bgg");
              softly.assertAll();
            });
  }

  @Test
  public void testStability2() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID2);
    assertThat(account.getDevices()).hasSize(3);
    assertThat(account.getProfileName())
        .isEqualTo(
            "dWFTKZe4Sp8F9xMGihXt9DC3SExBPkfWdRH4ks3j90lhW41hC7V6uIwb4RELSbr0jEAOXiDJozUkpgyefZkWqE8w49c/AplLSEzOcoBn6oqp6UNBSC9BAbnsB0etKKSGH+dW7YZsaQDeOkmuzUsSxNf81yMHhseu2i39gFfbAkXyzOS/7/tebRRXKSEcE7gZJD1ymOHHITiu8T/RnvYIaxIBduaHTN6sxpjMx8tEI47fN0HqPcwMNPkho8uW7V/g1m0eVpfsbzAydG0HxCiYSpLX8G03sSb+4ypA89xexRyffzkxw+ghZrW6qp5VEdkrEjc8DsqjqHlQkN8npVNy79CVe58RHdBNilR1iP0ckN+C4I5FglX4bcSZoGFa");
    assertThat(account.getProfileEmailAddress())
        .isEqualTo(
            "vyWaMrB1TW+l96D9AEYKE4iyhPq7YZKMoO6XLWdZwHPiXpwB8zNOgjTa0aTDjmUbussfPV9Xzp6b2utVMA5M1Ep+tTogLv4+qSEXsxWkKKgteOj3+gHl1KWeU0GIfRV8KqFM8IRSoW6Yv0o0Fq5X2JV4gDnDUkINlYYAkJtzrG68fxkqMW/5xKdxDwHCZkFdSbMewwIkPPr1KMx1AJC/mWHHrR6an+1ScmexRcFt3Bsh67ufF4Ve31k4EZKHrR7OPbeGrOem6wIMfijBIfGSW0s2Xw64R0qfu2hmP1Fd3zMqXj6bzX2616cwQtxytv/nlzCymwI29pTlcJ49qBlS6OBhdlUfyCSS0Ootanb3AsGoBEMMMOi1qh92zVmvpv7dExhia4KNdhg8i4U49CXI1xpcZfOk0nyV6BEAzngGW6bFpG2JPnwM6cZHysjLhxWNIhqyW+3nMzGh9opn");
    assertThat(account.getAvatar()).isNull();
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.isGroupsV2Supported()).isFalse();
    assertThat(account.isUnrestrictedUnidentifiedAccess()).isFalse();
    assertThat(account.getIdentityKey()).isEqualTo("Bc8gsBK5gVR8nE0ng6adCAHb51QDSYx7+hB4xb4Rkk5Z");

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
                  .isEqualTo("BendFIcGg4ZabxlyLGofaDcy3Osc9E3qOQFYDrQzbZ0Y");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "6fY0rQydf4hJkKggEU3oI3N8DgdiFly2eWzU9qyU9orcehJHLKl4dA2jWJYGfpi0f0Xaj/6R0iCLu6vkPRIXiQ");
              softly.assertAll();
            });
    assertThat(account.getDevices())
        .filteredOn("id", 3L)
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getRegistrationId()).isEqualTo(10477);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(5723057L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("Bcc0YUN/jscU2ZjRKbCVu0t/tKy2isiNB2BXogFBXVMM");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "+eVx89ysIx+Aw6XafdQ1n1WzOOihwIbPN1Qe7O49xOwPsOION4F/6/EBND+JQ+FEvv+RTrKqoigmPXFC1UCGjg");
              softly.assertAll();
            });
    assertThat(account.getDevices())
            .filteredOn("id", 2L)
            .hasOnlyOneElementSatisfying(
                    device -> {
                      SoftAssertions softly = new SoftAssertions();
                      softly.assertThat(device.getRegistrationId()).isEqualTo(2978);
                      softly.assertThat(device.getRealDevice()).isNotPresent();
                      softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(9347985L);
                      softly
                              .assertThat(device.getSignedPreKey().getPublicKey())
                              .isEqualTo("BdVshfmombztWGOJjkI1mh0RDPddv7J1SghZNlIih6M9");
                      softly
                              .assertThat(device.getSignedPreKey().getSignature())
                              .isEqualTo(
                                      "IeDFZEweN5quXNRo5WDHon+MKKp3r6S9n7BTH2zeCHArDhbdxaXYbrK4H2UTvIfEubMuFPD5epwbPnGeZbVBig");
                      softly.assertAll();
                    });
  }
}
