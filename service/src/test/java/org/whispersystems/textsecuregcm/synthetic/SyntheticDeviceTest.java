package org.whispersystems.textsecuregcm.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.whispersystems.textsecuregcm.entities.PreKey;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;

import java.util.Optional;

import static org.mockito.Mockito.mock;

public class SyntheticDeviceTest {

  private static final long DEVICE_ID = 1L;
  private static final int REGISTRATION_ID = 123;

  @Test
  public void when_generateUniqueSyntheticPreKey_manyTimes_then_nonNegativePreKeyIds() {
    SignedPreKey signedPreKey = mock(SignedPreKey.class);
    SyntheticDevice device = new SyntheticDevice(DEVICE_ID, REGISTRATION_ID, signedPreKey);
    for (int i = 0; i < 100; ++i) {
      Optional<PreKey> preKey = device.generateUniqueSyntheticPreKey();
      assertThat(preKey).isPresent();
      assertThat(preKey.get().getKeyId()).isGreaterThanOrEqualTo(0L);
    }
  }
}
