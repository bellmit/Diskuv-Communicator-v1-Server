package org.whispersystems.textsecuregcm.tests.controllers;

import com.amazonaws.services.s3.AmazonS3;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
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
import com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
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
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.storage.VersionedProfile;
import org.whispersystems.textsecuregcm.synthetic.HmacDrbg;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticProfilesManager;
import org.whispersystems.textsecuregcm.synthetic.SyntheticVersionedProfile;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class ProfileControllerTest {

  private static PossiblySyntheticAccountsManager  accountsManager     = mock(PossiblySyntheticAccountsManager.class );
  private static PossiblySyntheticProfilesManager  profilesManager     = mock(PossiblySyntheticProfilesManager.class);
  private static UsernamesManager usernamesManager    = mock(UsernamesManager.class);
  private static RateLimiters     rateLimiters        = mock(RateLimiters.class    );
  private static RateLimiter      rateLimiter         = mock(RateLimiter.class     );
  private static RateLimiter      usernameRateLimiter = mock(RateLimiter.class     );
  private static String           someEmail           = Strings.repeat("1234567890", 46) + "1234";

  private static AmazonS3                  s3client            = mock(AmazonS3.class);
  private static PostPolicyGenerator       postPolicyGenerator = new PostPolicyGenerator("us-west-1", "profile-bucket", "accessKey");
  private static PolicySigner              policySigner        = new PolicySigner("accessSecret", "us-west-1");
  private static ServerZkProfileOperations zkProfileOperations = mock(ServerZkProfileOperations.class);


  @ClassRule
  public static final ResourceTestRule resources = ResourceTestRule.builder()
                                                                   .addProvider(AuthHelper.getAuthFilter())
                                                                   .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                                                                   .setMapper(SystemMapper.getMapper())
                                                                   .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                                                                   .addResource(new ProfileController(rateLimiters,
                                                                                                      accountsManager,
                                                                                                      profilesManager,
                                                                                                      usernamesManager,
                                                                                                      s3client,
                                                                                                      postPolicyGenerator,
                                                                                                      policySigner,
                                                                                                      "profilesBucket",
                                                                                                      zkProfileOperations,
                                                                                                      true))
                                                                   .build();

  @Before
  public void setup() throws Exception {
    when(rateLimiters.getProfileLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getUsernameLookupLimiter()).thenReturn(usernameRateLimiter);

    Account profileAccount = mock(Account.class);

    when(profileAccount.getIdentityKey()).thenReturn("bar");
    when(profileAccount.getProfileName()).thenReturn("baz");
    when(profileAccount.getAvatar()).thenReturn("profiles/bang");
    when(profileAccount.getAvatarDigest()).thenReturn("buh");
    when(profileAccount.getUuid()).thenReturn(AuthHelper.VALID_UUID_TWO);
    when(profileAccount.isEnabled()).thenReturn(true);
    when(profileAccount.isUuidAddressingSupported()).thenReturn(false);

    Account capabilitiesAccount = mock(Account.class);

    when(capabilitiesAccount.getIdentityKey()).thenReturn("barz");
    when(capabilitiesAccount.getProfileName()).thenReturn("bazz");
    when(capabilitiesAccount.getAvatar()).thenReturn("profiles/bangz");
    when(capabilitiesAccount.getAvatarDigest()).thenReturn("buz");
    when(capabilitiesAccount.isEnabled()).thenReturn(true);
    when(capabilitiesAccount.isUuidAddressingSupported()).thenReturn(true);

    when(accountsManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(profileAccount);
    when(usernamesManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of("n00bkiller"));
    when(usernamesManager.get("n00bkiller")).thenReturn(Optional.of(AuthHelper.VALID_UUID_TWO));

    when((Optional<SyntheticVersionedProfile>)profilesManager.get(eq(AuthHelper.VALID_UUID), eq("someversion"))).     thenReturn(Optional.of(new SyntheticVersionedProfile(new byte[HmacDrbg.ENTROPY_INPUT_SIZE_BYTES], AuthHelper.VALID_UUID)));
    when((Optional<VersionedProfile>)         profilesManager.get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"))).thenReturn(Optional.of(new VersionedProfile("validversion", "validname", "profiles/validavatar", "profiles/validemail", "validcommitmnet".getBytes())));

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
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("profiles/bang");
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");

    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(rateLimiter, times(1)).validate(eq(AuthHelper.VALID_NUMBER));
  }

  @Test
  public void testProfileGetByNumberBadRequest() throws RateLimitExceededException {
    Response response = resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_NUMBER_TWO)
                              .request()
                              .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                              .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get();

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
              .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
              .get(Profile.class);
    });
  }

  @Test
  public void testProfileGetByUuidUnauthorized() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void testProfileGetByUsernameUnauthorized() throws Exception {
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
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get();

    // Diskuv Change: Do not allow profile retrieval by username, since no access control on profile retrieval
    // So profile retrieval target path is not present (404).
    assertThat(response.getStatus()).isEqualTo(404);
  }


  @Test
  public void testProfileGetByUuidDisabled() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_NUMBER, AuthHelper.DISABLED_PASSWORD))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @Ignore("Deleted deprecated non-versioned REST endpoints in ProfileController")
  public void testProfileCapabilities() throws Exception {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_NUMBER)
                              .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getCapabilities().isUuid()).isTrue();
  }

  @Test
  @Ignore("Deleted deprecated non-versioned REST endpoints in ProfileController")
  public void testSetProfileNameDeprecated() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012")
                                 .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
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
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
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
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
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
                                                              .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                                              .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", true, someEmail), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID), eq("someversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID), profileArgumentCaptor.capture());

    verifyNoMoreInteractions(s3client);

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).isEqualTo(uploadAttributes.getKey());
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("someversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getEmailAddress()).isEqualTo(someEmail);
  }

  @Test
  public void testSetProfileWantAvatarUploadWithBadProfileSize() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/")
                                 .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.entity(new CreateProfileRequest(commitment, "someversion", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890", true, "someemail"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testSetProfileWithoutAvatarUpload() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID);

    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                 .put(Entity.entity(new CreateProfileRequest(commitment, "anotherversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", false, someEmail), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isFalse();

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("anotherversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());

    verifyNoMoreInteractions(s3client);

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).isNull();
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("anotherversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getEmailAddress()).isEqualTo(someEmail);
  }

  @Test
  public void testSetProvfileWithAvatarUploadAndPreviousAvatar() throws InvalidInputException {
    ProfileKeyCommitment commitment = new ProfileKey(new byte[32]).getCommitment(AuthHelper.VALID_UUID_TWO);

    ProfileAvatarUploadAttributes uploadAttributes= resources.getJerseyTest()
                                                             .target("/v1/profile/")
                                                             .request()
                                                             .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                                             .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                                             .put(Entity.entity(new CreateProfileRequest(commitment, "validversion", "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678", true, someEmail), MediaType.APPLICATION_JSON_TYPE), ProfileAvatarUploadAttributes.class);

    ArgumentCaptor<VersionedProfile> profileArgumentCaptor = ArgumentCaptor.forClass(VersionedProfile.class);

    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));
    verify(profilesManager, times(1)).set(eq(AuthHelper.VALID_UUID_TWO), profileArgumentCaptor.capture());
    verify(s3client, times(1)).deleteObject(eq("profilesBucket"), eq("profiles/validavatar"));

    assertThat(profileArgumentCaptor.getValue().getCommitment()).isEqualTo(commitment.serialize());
    assertThat(profileArgumentCaptor.getValue().getAvatar()).startsWith("profiles/");
    assertThat(profileArgumentCaptor.getValue().getVersion()).isEqualTo("validversion");
    assertThat(profileArgumentCaptor.getValue().getName()).isEqualTo("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678");
    assertThat(profileArgumentCaptor.getValue().getEmailAddress()).isEqualTo(someEmail);
  }

  @Test
  public void testGetProfileByVersion() throws RateLimitExceededException {
    Profile profile = resources.getJerseyTest()
                               .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO + "/validversion")
                               .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                               .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("validname");
    assertThat(profile.getAvatar()).isEqualTo("profiles/validavatar");
    assertThat(profile.getCapabilities().isUuid()).isFalse();
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");
    assertThat(profile.getUuid()).isNull();;

    verify(accountsManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(profilesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"));

    verify(rateLimiter, times(1)).validate(eq(AuthHelper.VALID_NUMBER));
  }


}
