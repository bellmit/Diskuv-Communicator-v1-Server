package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.whispersystems.textsecuregcm.util.ExactlySize;

import javax.validation.constraints.NotNull;

public class CreateProfileRequest {

  @JsonProperty
  @NotEmpty
  private String version;

  @JsonProperty
  @ExactlySize({108, 380})
  private String name;

  @JsonProperty
  private boolean avatar;

  @JsonProperty
  @ExactlySize({464})
  private String emailAddress;

  @ExactlySize({0, 80})
  private String aboutEmoji;

  @JsonProperty
  @ExactlySize({0, 208, 376, 720})
  private String about;

  @JsonProperty
  @NotNull
  @JsonDeserialize(using = ProfileKeyCommitmentAdapter.Deserializing.class)
  @JsonSerialize(using = ProfileKeyCommitmentAdapter.Serializing.class)
  private ProfileKeyCommitment commitment;

  public CreateProfileRequest() {}

  public CreateProfileRequest(ProfileKeyCommitment commitment, String version, String name, String emailAddress, String aboutEmoji, String about, boolean wantsAvatar) {
    this.commitment = commitment;
    this.version    = version;
    this.name       = name;
    this.emailAddress = emailAddress;
    this.aboutEmoji = aboutEmoji;
    this.about      = about;
    this.avatar     = wantsAvatar;
  }

  public ProfileKeyCommitment getCommitment() {
    return commitment;
  }

  public String getVersion() {
    return version;
  }

  public String getName() {
    return name;
  }

  public boolean isAvatar() {
    return avatar;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public String getAboutEmoji() {
    return StringUtils.stripToNull(aboutEmoji);
  }

  public String getAbout() {
    return StringUtils.stripToNull(about);
  }
}
