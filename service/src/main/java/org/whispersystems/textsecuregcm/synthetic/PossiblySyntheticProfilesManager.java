package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.storage.ProfilesManager;
import org.whispersystems.textsecuregcm.storage.VersionedProfile;

import java.util.Optional;
import java.util.UUID;

public class PossiblySyntheticProfilesManager {
  private final ProfilesManager profilesManager;
  private final byte[] sharedEntropyInput;

  public PossiblySyntheticProfilesManager(
      ProfilesManager profilesManager, byte[] sharedEntropyInput) {
    this.profilesManager = profilesManager;
    this.sharedEntropyInput = sharedEntropyInput;
  }

  public Optional<PossiblySyntheticVersionedProfile> get(UUID accountUuid, String version) {
    Optional<VersionedProfile> profile = profilesManager.get(accountUuid, version);
    if (profile.isPresent()) {
      return Optional.of(profile.get());
    }
    SyntheticVersionedProfile syntheticProfile = new SyntheticVersionedProfile(sharedEntropyInput, accountUuid);
    if (syntheticProfile.getKeyVersion().equals(version)) {
      return Optional.of(syntheticProfile);
    }
    return Optional.empty();
  }

  public void set(UUID uuid, PossiblySyntheticVersionedProfile versionedProfile) {
    versionedProfile
        .getRealVersionedProfile()
        .ifPresent(realVersionedProfile -> profilesManager.set(uuid, realVersionedProfile));
  }
}
