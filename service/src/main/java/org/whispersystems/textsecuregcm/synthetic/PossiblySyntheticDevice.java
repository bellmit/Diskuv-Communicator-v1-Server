package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.entities.PreKey;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.storage.Device;

import java.util.Optional;

public interface PossiblySyntheticDevice {
  Optional<Device> getRealDevice();

  Optional<PreKey> generateUniqueSyntheticPreKey();

  boolean isEnabled();

  long getId();

  SignedPreKey getSignedPreKey();

  int getRegistrationId();
}
