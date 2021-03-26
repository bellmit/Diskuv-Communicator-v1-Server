package org.whispersystems.textsecuregcm.tests.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicConfiguration;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicPaymentsConfiguration;
import org.whispersystems.textsecuregcm.controllers.ProfileController;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.entities.CreateProfileRequest;
import org.whispersystems.textsecuregcm.entities.Profile;
import org.whispersystems.textsecuregcm.entities.ProfileAvatarUploadAttributes;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.PolicySigner;
import org.whispersystems.textsecuregcm.s3.PostPolicyGenerator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.DynamicConfigurationManager;
import org.whispersystems.textsecuregcm.storage.ProfilesManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.storage.VersionedProfile;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;
import org.whispersystems.textsecuregcm.util.SystemMapper;
import org.whispersystems.textsecuregcm.util.Util;

import static org.junit.Assert.assertThrows;

public class ProfileControllerTest {

  private static AccountsManager accountsManager     = mock(AccountsManager.class );
  private static org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager  possiblySyntheticAccountsManager     = new org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager(accountsManager, new byte[org.whispersystems.textsecuregcm.synthetic.HmacDrbg.ENTROPY_INPUT_SIZE_BYTES]);
  private static org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticProfilesManager  profilesManager     = mock(org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticProfilesManager.class);
  private static UsernamesManager usernamesManager    = mock(UsernamesManager.class);
  private static RateLimiters     rateLimiters        = mock(RateLimiters.class    );
  private static RateLimiter      rateLimiter         = mock(RateLimiter.class     );
  private static RateLimiter      usernameRateLimiter = mock(RateLimiter.class     );
  private static String           someEmail           = Strings.repeat("1234567890", 46) + "1234";

  private static AmazonS3                  s3client            = mock(AmazonS3.class);
  private static PostPolicyGenerator       postPolicyGenerator = new PostPolicyGenerator("us-west-1", "profile-bucket", "accessKey");
  private static PolicySigner              policySigner        = new PolicySigner("accessSecret", "us-west-1");
  private static ServerZkProfileOperations zkProfileOperations = mock(ServerZkProfileOperations.class);

  private static DynamicConfigurationManager dynamicConfigurationManager = mock(DynamicConfigurationManager.class);
  private DynamicPaymentsConfiguration dynamicPaymentsConfiguration;

  private Account profileAccount;


  @ClassRule
  public static final ResourceTestRule resources = ResourceTestRule.builder()
                                                                   .addProvider(AuthHelper.getAuthFilter())
                                                                   .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                                                                   .setMapper(SystemMapper.getMapper())
                                                                   .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                                                                   .addResource(new ProfileController(rateLimiters,
                                                                                                      possiblySyntheticAccountsManager,
                                                                                                      profilesManager,
                                                                                                      usernamesManager,
                                                                                                      dynamicConfigurationManager,
                                                                                                      s3client,
                                                                                                      postPolicyGenerator,
                                                                                                      policySigner,
                                                                                                      "profilesBucket",
                                                                                                      zkProfileOperations,
                                                                                                      true))
                                                                   .build();

  @Before
  public void setup() throws Exception {
    reset(s3client);

    dynamicPaymentsConfiguration = mock(DynamicPaymentsConfiguration.class);
    final DynamicConfiguration dynamicConfiguration = mock(DynamicConfiguration.class);

    when(dynamicConfigurationManager.getConfiguration()).thenReturn(dynamicConfiguration);
    when(dynamicConfiguration.getPaymentsConfiguration()).thenReturn(dynamicPaymentsConfiguration);
    when(dynamicPaymentsConfiguration.getAllowedCountryCodes()).thenReturn(Collections.emptySet());

    when(rateLimiters.getProfileLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getUsernameLookupLimiter()).thenReturn(usernameRateLimiter);

    profileAccount = mock(Account.class);

    when(profileAccount.getIdentityKey()).thenReturn("bar");
    when(profileAccount.getProfileName()).thenReturn("baz");
    when(profileAccount.getAvatar()).thenReturn("profiles/bang");
    when(profileAccount.getUuid()).thenReturn(AuthHelper.VALID_UUID_TWO);
    when(profileAccount.isEnabled()).thenReturn(true);
    when(profileAccount.isGroupsV2Supported()).thenReturn(false);
    when(profileAccount.isGv1MigrationSupported()).thenReturn(false);
    when(profileAccount.getCurrentProfileVersion()).thenReturn(Optional.empty());

    Account capabilitiesAccount = mock(Account.class);

    when(capabilitiesAccount.getIdentityKey()).thenReturn("barz");
    when(capabilitiesAccount.getProfileName()).thenReturn("bazz");
    when(capabilitiesAccount.getAvatar()).thenReturn("profiles/bangz");
    when(capabilitiesAccount.isEnabled()).thenReturn(true);
    when(capabilitiesAccount.isGroupsV2Supported()).thenReturn(true);
    when(capabilitiesAccount.isGv1MigrationSupported()).thenReturn(true);

    when(accountsManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of(profileAccount));
    when(usernamesManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of("n00bkiller"));
    when(usernamesManager.get("n00bkiller")).thenReturn(Optional.of(AuthHelper.VALID_UUID_TWO));

    when(profilesManager.get(eq(AuthHelper.VALID_UUID), eq("someversion"))).thenReturn(Optional.empty());
    when(profilesManager.get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"))).thenReturn(Optional.of(new VersionedProfile(
        "validversion", "validname", "profiles/validavatar", "profiles/validemail", "emoji", "about", null, "validcommitmnet".getBytes())));

    when(accountsManager.get(AuthHelper.VALID_UUID)).thenReturn(Optional.of(capabilitiesAccount));

    clearInvocations(rateLimiter);
    clearInvocations(accountsManager);
    clearInvocations(usernamesManager);
    clearInvocations(usernameRateLimiter);
    clearInvocations(profilesManager);
  }

  @Test
  public void testProfileGetByUuid() throws RateLimitExceededException {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
                              .request()
                              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                              .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                              .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("profiles/bang");
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");

    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(rateLimiter, times(1)).validate(eq(AuthHelper.VALID_UUID_TWO.toString()));
  }

  @Ignore("Diskuv does not support profile lookups by phone number")
  @Test
  public void testProfileGetByNumberBadRequest() throws RateLimitExceededException {
    Profile profile = resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_NUMBER_TWO)
                              .request()
                              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                              .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("profiles/bang");
    assertThat(profile.getCapabilities().isGv2()).isFalse();
    assertThat(profile.getCapabilities().isGv1Migration()).isFalse();
    assertThat(profile.getUsername()).isNull();
    assertThat(profile.getUuid()).isNull();
  }

  @Test
  public void testProfileGetByUsername() throws RateLimitExceededException {
    // Diskuv Change: Do not allow profile retrieval by username, since no access control on profile retrieval
    // So profile retrieval target path is not present.
    assertThrows(javax.ws.rs.NotFoundException.class, () -> {
      resources.getJerseyTest()
              .target("/v1/profile/username/n00bkiller")
              .request()
              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
              .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
              .get(Profile.class);
    });
  }

  @Test
  public void testProfileGetUnauthorized() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void testProfileGetByUsernameUnauthorized() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/username/n00bkiller")
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }


  @Test
  public void testProfileGetByUsernameNotFound() throws RateLimitExceededException {
    Response response = resources.getJerseyTest()
                              .target("/v1/profile/username/n00bkillerzzzzz")
                              .request()
                              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                              .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get();

    // Diskuv Change: Do not allow profile retrieval by username, since no access control on profile retrieval
    // So profile retrieval target path is not present (404).
    assertThat(response.getStatus()).isEqualTo(404);
  }


  @Test
  public void testProfileGetDisabled() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.DISABLED_BEARER_TOKEN))
                                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.DISABLED_DEVICE_ID_STRING, AuthHelper.DISABLED_PASSWORD))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Ignore("Deleted deprecated non-versioned REST endpoints in ProfileController")
  @Test
  public void testProfileCapabilities() {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_UUID)
                              .request()
                              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                              .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getCapabilities().isGv2()).isTrue();
    assertThat(profile.getCapabilities().isGv1Migration()).isTrue();
  }

  @Test
  @Ignore("Deleted deprecated non-versioned REST endpoints in ProfileController")
  public void testSetProfileNameDeprecated() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(accountsManager, times(1)).update(any(Account.class));
  }

  @Test
  @Ignore("Deleted deprecated non-versioned REST endpoints in ProfileController")
  public void testSetProfileNameExtendedDeprecated() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(accountsManager, times(1)).update(any(Account.class));
  }

  @Test
  @Ignore("Deleted deprecated non-versioned REST endpoints in ProfileController")
  public void testSetProfileNameWrongSizeDeprecated() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
    verifyNoMoreInteractions(accountsManager);
  }

  /////

  @Test
  public void testSetProfileWantAvatarUpload() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    ProfileAvatarUploadAttributes uploadAttributes = resources.getJerseyTest()
                                                              .target("/v1/profile/")
                                                              .request()
                                                              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                                              .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                                              .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", someEmail, null, null,
                                                                  null, true), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID), eq("someversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID), profileArgumentCaptor.capture());

    verifyNoMoreInteractions(s3client);

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).isEqualTo(uploadAttributes.getKey());
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("someversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getEmailAddress()).isEqualTo(someEmail);
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();  }

  @Test
  public void testSetProfileWantAvatarUploadWithBadProfileSize() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890", "someemail", null, null,
                                     null, true), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testSetProfileWithoutAvatarUpload() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                 .put(Entity.entity(new CreateProfileRequest(commitment, "anotherversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", someEmail, null, null,
                                     null, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("anotherversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verify(AuthHelper.VALID_ACCOUNT_TWO).setProfileName("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    verify(AuthHelper.VALID_ACCOUNT_TWO).setAvatar(null);

    verifyNoMoreInteractions(s3client);

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).isNull();
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("anotherversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getEmailAddress()).isEqualTo(someEmail);
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();
  }

  @Test
  public void testSetProfileWithAvatarUploadAndPreviousAvatar() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    ProfileAvatarUploadAttributes uploadAttributes= resources.getJerseyTest()
                                                             .target("/v1/profile/")
                                                             .request()
                                                             .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                                             .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                                             .put(Entity.entity(new CreateProfileRequest(commitment, "validversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", someEmail, null, null,
                                                                 null, true), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());
    verify(s3client, times(1)).deleteObject(eq("profilesBucket"), eq("profiles/validavatar"));

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).startsWith("profiles/");
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("validversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getEmailAddress()).isEqualTo(someEmail);
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();  }

  @Test
  public void testSetProfileExtendedName() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String email = RandomStringUtils.randomAlphabetic(464);

    resources.getJerseyTest()
            .target("/v1/profile/")
            .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
            .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
            .put(Entity.entity(new CreateProfileRequest(commitment, "validversion", name, email, null, null, null, true), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());
    verify(s3client, times(1)).deleteObject(eq("profilesBucket"), eq("profiles/validavatar"));

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).startsWith("profiles/");
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("validversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo(name);
    assertThat(profileArgumentCaptor.getValue().getAboutEmoji()).isNull();
    assertThat(profileArgumentCaptor.getValue().getAbout()).isNull();
  }

  @Test
  public void testSetProfileEmojiAndBioText() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String email = RandomStringUtils.randomAlphabetic(464);
    final String emoji = RandomStringUtils.randomAlphanumeric(80);
    final String text = RandomStringUtils.randomAlphanumeric(720);

    Response response = resources.getJerseyTest()
            .target("/v1/profile/")
            .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
            .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
            .put(Entity.entity(new CreateProfileRequest(commitment, "anotherversion", name, email, emoji, text, null, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("anotherversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verify(AuthHelper.VALID_ACCOUNT_TWO).setProfileName(name);
    verify(AuthHelper.VALID_ACCOUNT_TWO).setAvatar(null);

    verifyNoMoreInteractions(s3client);

    final VersionedProfile profile = profileArgumentCaptor.getValue();
    assertThat(profile.getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profile.getAvatar()).isNull();
    assertThat(profile.getVersion()).isEqualTo("anotherversion");
    assertThat(profile.getName()).isEqualTo(name);
    assertThat(profile.getAboutEmoji()).isEqualTo(emoji);
    assertThat(profile.getAbout()).isEqualTo(text);
    assertThat(profile.getPaymentAddress()).isNull();
  }

  @Test
  public void testSetProfilePaymentAddress() throws InvalidInputException {
    when(dynamicPaymentsConfiguration.getAllowedCountryCodes())
        .thenReturn(Set.of(Util.getCountryCode(AuthHelper.VALID_NUMBER_TWO)));

    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String email = RandomStringUtils.randomAlphabetic(464);
    final String paymentAddress = RandomStringUtils.randomAlphanumeric(776);

    Response response = resources.getJerseyTest()
        .target("/v1/profile")
        .request()
        .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
        .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "yetanotherversion", name, email, null, null, paymentAddress, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager).get(eq(AuthHelper.VALID_UUID_TWO), eq("yetanotherversion"));
    verify(profilesManager).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verify(AuthHelper.VALID_ACCOUNT_TWO).setProfileName(eq(name));
    verify(AuthHelper.VALID_ACCOUNT_TWO).setAvatar(null);

    verifyNoMoreInteractions(s3client);

    final VersionedProfile profile = profileArgumentCaptor.getValue();
    assertThat(profile.getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profile.getAvatar()).isNull();
    assertThat(profile.getVersion()).isEqualTo("yetanotherversion");
    assertThat(profile.getName()).isEqualTo(name);
    assertThat(profile.getAboutEmoji()).isNull();
    assertThat(profile.getAbout()).isNull();
    assertThat(profile.getPaymentAddress()).isEqualTo(paymentAddress);
  }

  @Test
  public void testSetProfilePaymentAddressCountryNotAllowed() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String paymentAddress = RandomStringUtils.randomAlphanumeric(776);

    Response response = resources.getJerseyTest()
        .target("/v1/profile")
        .request()
        .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
        .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "yetanotherversion", name, "email", null, null, paymentAddress, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();

    verify(profilesManager, never()).set(any(), any());
  }

  @Test
  public void testGetProfileByVersion() throws RateLimitExceededException {
    Profile profile = resources.getJerseyTest()
                               .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
                               .request()
                               .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                               .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                               .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("validname");
    assertThat(profile.getAbout()).isEqualTo("about");
    assertThat(profile.getAboutEmoji()).isEqualTo("emoji");
    assertThat(profile.getAvatar()).isEqualTo("profiles/validavatar");
    assertThat(profile.getCapabilities().isGv2()).isFalse();
    assertThat(profile.getCapabilities().isGv1Migration()).isFalse();
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");
    assertThat(profile.getUuid()).isNull();

    verify(accountsManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));

    verify(rateLimiter, times(1)).validate(eq(AuthHelper.VALID_UUID_TWO.toString()));
  }

  @Test
  public void testSetProfileUpdatesAccountCurrentVersion() throws InvalidInputException {
    when(dynamicPaymentsConfiguration.getAllowedCountryCodes())
        .thenReturn(Set.of(Util.getCountryCode(AuthHelper.VALID_NUMBER_TWO)));

    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    clearInvocations(AuthHelper.VALID_ACCOUNT_TWO);

    final String name = RandomStringUtils.randomAlphabetic(380);
    final String email = RandomStringUtils.randomAlphabetic(464);
    final String paymentAddress = RandomStringUtils.randomAlphanumeric(776);

    Response response = resources.getJerseyTest()
        .target("/v1/profile")
        .request()
        .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
        .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", name, email, null, null, paymentAddress, false), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    verify(AuthHelper.VALID_ACCOUNT_TWO).setCurrentProfileVersion("someversion");
  }

  @org.junit.Ignore("Diskuv does not do payments")
  @Test
  public void testGetProfileReturnsNoPaymentAddressIfCurrentVersionMismatch() {
    when(profilesManager.get(AuthHelper.VALID_UUID_TWO, "validversion")).thenReturn(
        Optional.of(new VersionedProfile(null, null, null, null, null, null, "paymentaddress", null)));
    Profile profile = resources.getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
        .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .get(Profile.class);
    assertThat(profile.getPaymentAddress()).isEqualTo("paymentaddress");

    when(profileAccount.getCurrentProfileVersion()).thenReturn(Optional.of("validversion"));
    profile = resources.getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
        .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .get(Profile.class);
    assertThat(profile.getPaymentAddress()).isEqualTo("paymentaddress");

    when(profileAccount.getCurrentProfileVersion()).thenReturn(Optional.of("someotherversion"));
    profile = resources.getJerseyTest()
        .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
        .request()
        .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
        .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
        .get(Profile.class);
    assertThat(profile.getPaymentAddress()).isNull();
  }
}
