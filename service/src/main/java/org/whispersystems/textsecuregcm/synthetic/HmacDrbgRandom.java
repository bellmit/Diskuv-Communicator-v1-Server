package org.whispersystems.textsecuregcm.synthetic;

import java.util.Random;

public class HmacDrbgRandom extends Random {
  private final HmacDrbg drbg;

  public HmacDrbgRandom(HmacDrbg drbg) {
    super(0L);
    this.drbg = drbg;
  }

  @Override
  public void setSeed(long l) {
  }

  @Override
  protected int next(int numBits) {
    int numBytes = (numBits + 7) / 8;
    byte[] b = new byte[numBytes];
    int next = 0;
    this.nextBytes(b);

    for (int i = 0; i < numBytes; ++i) {
      next = (next << 8) + (b[i] & 255);
    }

    return next >>> numBytes * 8 - numBits;
  }

  @Override
  public void nextBytes(byte[] bytes) {
    drbg.nextBytes(bytes);
  }
}
