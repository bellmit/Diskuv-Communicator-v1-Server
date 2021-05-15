package org.whispersystems.textsecuregcm.synthetic;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.whispersystems.textsecuregcm.synthetic.SyntheticProfileStateTestCommons.*;

public class SyntheticProfileTest {

  @Test
  public void testStability1() {
    SyntheticVersionedProfile profile = new SyntheticVersionedProfile(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], UUID1);
    assertThat(profile.getRealVersionedProfile()).isNotPresent();
    assertThat(profile.getKeyVersion()).isEqualTo(KEYVERSION1);
    assertThat(profile.getName()).isEqualTo(NAME1);
    assertThat(profile.getEmailAddress()).isEqualTo(EMAIL1);
    assertThat(profile.getAvatar()).isEqualTo(AVATAR1);
    assertThat(profile.getAbout()).isEqualTo(ABOUT1);
    assertThat(profile.getAboutEmoji()).isEqualTo(ABOUTEMOJI1);
    assertThat(profile.getCommitment()).containsExactly(COMMITMENT1);
  }
}
