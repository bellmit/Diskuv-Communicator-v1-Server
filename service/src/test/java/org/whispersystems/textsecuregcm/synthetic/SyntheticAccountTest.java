package org.whispersystems.textsecuregcm.synthetic;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SyntheticAccountTest {
  private static final UUID UUID1 = DiskuvUuidUtil.uuidForEmailAddress("mistle-toe@equator.com");
  private static final UUID UUID2 = DiskuvUuidUtil.uuidForEmailAddress("mistle-toe2@equator.com");

  @Test
  public void testStability1() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID1);
    assertThat(account.getAvatar()).isNull();
    assertThat(account.getProfileName())
        .isEqualTo(
            "LvkzjLvGDjF3v+HYDz0ZDliWONAL+Cr2IQkuAYe6uAeQUpNdXKoip8/s088vRbYbE3VZXX1SKGQlfsA1OATq6tK4Ns3mcXAWWYv0Vmo476Gt");
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
                      "k3Nbi34WfVZTKhy6Dc6omARhrBCxDaUlGfJNNl+c9+E2e4O/3cpCaFc29UGXOyn487Qqo2dfxT6Ke2r8VdTegw");
              softly.assertAll();
            });
  }

  @Test
  public void testStability2() {
    SyntheticAccount account =
        new SyntheticAccount(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID2);
    assertThat(account.getAvatar()).isNull();
    assertThat(account.getProfileName())
        .isEqualTo(
            "o7+Yx4olkDbwNDVPwVPD9Ds1gB6RtG8wmZADUrXFBCGVvY00UuQGFOsphIR6gAQtsumhqsHQzfrenpneO/3RujnhJGLVE+mhtmjwr7qsvTdi");
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.isGroupsV2Supported()).isFalse();
    assertThat(account.isUnrestrictedUnidentifiedAccess()).isFalse();
    assertThat(account.getIdentityKey()).isEqualTo("BYwBDBUiyzOE391jbUBB1nhWtUeYDS3Bfywqn34GogN0");

    assertThat(account.getUnidentifiedAccessKey()).isNotPresent();
    assertThat(account.getDevices()).hasSize(2);
    assertThat(account.getAuthenticatedDevice()).isEqualTo(account.getDevice(1L));
    assertThat(account.getDevices())
        .filteredOn("id", 1L)
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getRegistrationId()).isEqualTo(598);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(6209960L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BcfvUbE+mVDkINm+kERU6h+Nz3obzX4bsvjx50fASxhQ");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "4NRV/awjX6BI4d4HYzHeSOAQs+w+fE8ZrXKHaaN8QsnqRWmht5g7MTBBd9JMYlGxUnwSw5oZdKsiF0EalANWBQ");
              softly.assertAll();
            });
    assertThat(account.getDevices())
        .filteredOn("id", 2L)
        .hasOnlyOneElementSatisfying(
            device -> {
              SoftAssertions softly = new SoftAssertions();
              softly.assertThat(device.getRegistrationId()).isEqualTo(13573);
              softly.assertThat(device.getRealDevice()).isNotPresent();
              softly.assertThat(device.getSignedPreKey().getKeyId()).isEqualTo(5714630L);
              softly
                  .assertThat(device.getSignedPreKey().getPublicKey())
                  .isEqualTo("BX07ht+5KIcoTBCk7qsig34MqBznaxNw5rtCw7llR4BV");
              softly
                  .assertThat(device.getSignedPreKey().getSignature())
                  .isEqualTo(
                      "+F0WR1zFAZejU6C9oEXKxL50hTDkxg9wyR6x5Qc+BhTeLypvZ3Z+bDENUmOv/10bPoS5YigTmuiGKV/IpFKTBw");
              softly.assertAll();
            });
  }
}
