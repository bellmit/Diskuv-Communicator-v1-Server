package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECKeyPair;
import org.whispersystems.textsecuregcm.entities.PreKey;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.Base64;

import java.security.SecureRandom;
import java.util.Optional;

import static org.whispersystems.textsecuregcm.synthetic.SyntheticAccount.MEDIUM_MAX_VALUE;

public class SyntheticDevice implements PossiblySyntheticDevice {
  private final long deviceId;
  private final int registrationId;
  private final SignedPreKey signedPreKey;


  public SyntheticDevice(long deviceId,
                         int registrationId,
                         SignedPreKey signedPreKey) {
    this.deviceId = deviceId;
    this.registrationId = registrationId;
    this.signedPreKey = signedPreKey;
  }

  @Override
  public String toString() {
    return "SyntheticDevice{" +
            "deviceId=" + deviceId +
            ", registrationId=" + registrationId +
            ", signedPreKey=" + signedPreKey +
            '}';
  }

  @Override
  public Optional<Device> getRealDevice() {
    return Optional.empty();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public long getId() {
    return deviceId;
  }

  @Override
  public SignedPreKey getSignedPreKey() {
    return signedPreKey;
  }

  @Override
  public int getRegistrationId() {
    return registrationId;
  }

  @Override
  public Optional<PreKey> generateUniqueSyntheticPreKey() {
    // confer: com.diskuv.communicator.crypto.PreKeyUtil#generatePreKeys
    // confer: org.whispersystems.signalservice.internal.push.PreKeyEntity.ECPublicKeySerializer
    int preKeyId           = new SecureRandom().nextInt(MEDIUM_MAX_VALUE);
    ECKeyPair keyPair      = Curve.generateKeyPair();
    String preKeyPublicKey = Base64.encodeBytesWithoutPadding(keyPair.getPublicKey().serialize());
    return Optional.of(new PreKey(preKeyId, preKeyPublicKey));
  }
}
