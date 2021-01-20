package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.storage.VersionedProfile;

import java.util.Optional;

public interface PossiblySyntheticVersionedProfile {
  Optional<VersionedProfile> getRealVersionedProfile();

  String getName();

  String getAvatar();

  String getEmailAddress();

  String getAboutEmoji();

  String getAbout();

  byte[] getCommitment();
}
