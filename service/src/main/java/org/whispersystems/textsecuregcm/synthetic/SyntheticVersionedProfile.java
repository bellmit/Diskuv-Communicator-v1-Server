package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.storage.VersionedProfile;

import java.util.Optional;
import java.util.UUID;

/** A deterministic versioned profile. Similar in design to {@link SyntheticAccount}. */
public class SyntheticVersionedProfile implements PossiblySyntheticVersionedProfile {
  private final SyntheticProfileState profileState;

  public SyntheticVersionedProfile(byte[] sharedEntropyInput, UUID accountUuid) {
    this.profileState = new SyntheticProfileState(sharedEntropyInput, accountUuid);
  }

  @Override
  public Optional<VersionedProfile> getRealVersionedProfile() {
    return Optional.empty();
  }

  public String getKeyVersion() {
    return profileState.getKeyVersion();
  }

  @Override
  public String getName() {
    return profileState.getName();
  }

  @Override
  public String getEmailAddress() {
    return profileState.getEmailAddress();
  }

  @Override
  public String getAboutEmoji() {
    return profileState.getAboutEmoji();
  }

  @Override
  public String getAbout() {
    return profileState.getAbout();
  }

  @Override
  public byte[] getCommitment() {
    return profileState.getCommitment();
  }

  @Override
  public String getAvatar() {
    return profileState.getAvatar();
  }
}
