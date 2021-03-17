package org.whispersystems.textsecuregcm.synthetic;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.whispersystems.textsecuregcm.synthetic.SyntheticProfileStateTestCommons.*;
import static org.whispersystems.textsecuregcm.synthetic.SyntheticProfileStateTestCommons.COMMITMENT1;

public class SyntheticAccountTest {
  private static final UUID UUID2 = DiskuvUuidUtil.uuidForEmailAddress("mistle-finger@equator.com");

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
    assertThat(account.getIdentityKey()).isEqualTo("BZ115mp5Cgfrh6rw+VNPsmlBgn7/JrLpuw4RvgMH+EZ9");

    assertThat(account.getUnidentifiedAccessKey()).isNotPresent();
    assertThat(account.getDevices()).hasSize(1);
    assertThat(account.getAuthenticatedDevice()).isEqualTo(account.getDevice(1L));
    assertThat(account.getDevices())
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getId()).isEqualTo(1L);
              softly.assertThat(device.getRegistrationId()).isEqualTo(12529);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(4498198L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BSDs5xOuPSn4d1IZZKxxTRG2Zn/KmtiZbjf8S0sZCFEF");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "adOun6NgyhsXlQvZ8V1Gk59gz680zCQSpoF1cWz3tyvacujA+LP7XdqLYsToZ50Tj2PXbUcDt/md5Pxvm5HchQ");
              softly.assertAll();
            });
  }

  @Test
  public void testStability2() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID2);
    assertThat(account.getDevices()).hasSize(2);
    assertThat(account.getProfileName())
        .isEqualTo(
            "EpvPjCbk6o4GPB6b5x8a1syBsQjm9N0n9apABU5liLvmhzmXnw8jSzWURqbgZD+lDICrnKmAaE8DQ3tTOkrXSoFcJept+edYikfpg6q9UYK+");
    assertThat(account.getProfileEmailAddress())
        .isEqualTo(
            "uiRelQQ/AfPohFUMEY5jtMiYBCz4Kpn1L1LsaK7UWicxAtCmCVbzcsqSaUyqkOK3tmOSFFQkd4xOz2ck3waHukAsWVVsh5+L+l2Z0vRI6omT9yL+J55iq8PppCcPWLl1+xOSKmqMoXVV4wWL2H7mmpDWKCqUm3ouhixjVWAd7+bfaq/+4EQxfWjVKLhJP+/kZUiUnG+4iXd//oyZHYO94z9xomPPfLemlJdHxdCoqf9fGRHTYMZQe7PweRnpKf64zPj4XrhAlWOBl/xGLVwxD1FUKXLU6Qyvs+hZr+Q3i7Yeki/cvfmaXCv0rh/cCJCdJp/8TIayUiUReVAHKEufJXdZCTRROlFnX7nGI7cgfbNs14LxAaC8StpZ2Vfak+eXVVTvY6wK5heCxYJ5NDcQeqXFXWH8oHaWZiwM89xwS2mSmbizNllwxHQcokgxebdOzLwJU4WENZaUyG9o");
    assertThat(account.getAvatar()).isNull();
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.isGroupsV2Supported()).isFalse();
    assertThat(account.isUnrestrictedUnidentifiedAccess()).isFalse();
    assertThat(account.getIdentityKey()).isEqualTo("BY4uiJADYSHFoL0KqmHjJQQ/Td5jUzBLYl/ZqWiD19BE");

    assertThat(account.getUnidentifiedAccessKey()).isNotPresent();
    assertThat(account.getAuthenticatedDevice()).isEqualTo(account.getDevice(1L));
    assertThat(account.getDevices())
        .filteredOn("id", 1L)
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getRegistrationId()).isEqualTo(2512);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(8970741L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BTB+DKZotUrLmiZUtX1CYvJJ6KFXOkDUI3qPcz+lU0I4");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "z/8UPusUDjr9y8EDf4WkFZwvL8YIYjvtHqGvXA0KmPL8v7jA01zCNnLcp3Sc+vinj/DFMMEPnEgM/Vu4PeS6Cw");
              softly.assertAll();
            });
    assertThat(account.getDevices())
        .filteredOn("id", 4L)
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getRegistrationId()).isEqualTo(12526);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(564255L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BUInL2rCO3yZhnxeO2czCPrZeEjHROHdwMeFc2PIiedi");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "0l2LVMSK6CbjiiCH6BUzJM4EN19qSnnAxQf6Gkv5j8dpyg5M9nJTkuUYTnK72NCewyv63fLyWIouoQbUra3bBw");
              softly.assertAll();
            });
  }
}
