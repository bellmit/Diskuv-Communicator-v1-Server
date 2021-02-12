package org.whispersystems.textsecuregcm.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticVersionedProfile;
import org.whispersystems.textsecuregcm.util.ByteArrayAdapter;

import java.util.Optional;

public class VersionedProfile implements PossiblySyntheticVersionedProfile  {

  @JsonProperty
  private String version;

  @JsonProperty
  private String name;

  @JsonProperty
  private String avatar;

  @JsonProperty
  private String emailAddress;

  @JsonProperty
  private String aboutEmoji;

  @JsonProperty
  private String about;

  @JsonProperty
  private String paymentAddress;

  @JsonProperty
  @JsonSerialize(using = ByteArrayAdapter.Serializing.class)
  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
  private byte[] commitment;

  public VersionedProfile() {}

  public VersionedProfile(
      String version, String name, String avatar, String emailAddress, String aboutEmoji, String about, String paymentAddress,
      byte[] commitment) {
    this.version = version;
    this.name = name;
    this.avatar = avatar;
    this.emailAddress = emailAddress;
    this.aboutEmoji = aboutEmoji;
    this.about = about;
    this.paymentAddress = paymentAddress;
    this.commitment = commitment;
  }

  @Override
  public Optional<VersionedProfile> getRealVersionedProfile() {
    return Optional.of(this);
  }

  public String getVersion() {
    return version;
  }

  public String getName() {
    return name;
  }

  public String getAvatar() {
    return avatar;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public String getAboutEmoji() {
    return aboutEmoji;
  }

  public String getAbout() {
    return about;
  }

  public String getPaymentAddress() {
    return paymentAddress;
  }

  public byte[] getCommitment() {
    return commitment;
  }
}
