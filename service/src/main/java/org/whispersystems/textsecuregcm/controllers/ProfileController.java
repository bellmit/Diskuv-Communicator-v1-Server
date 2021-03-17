package org.whispersystems.textsecuregcm.controllers;

import com.amazonaws.services.s3.AmazonS3;
import com.codahale.metrics.annotation.Timed;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialResponse;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.Anonymous;
import org.whispersystems.textsecuregcm.auth.OptionalAccess;
import org.whispersystems.textsecuregcm.auth.UnidentifiedAccessChecksum;
import org.whispersystems.textsecuregcm.entities.CreateProfileRequest;
import org.whispersystems.textsecuregcm.entities.Profile;
import org.whispersystems.textsecuregcm.entities.ProfileAvatarUploadAttributes;
import org.whispersystems.textsecuregcm.entities.UserCapabilities;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.PolicySigner;
import org.whispersystems.textsecuregcm.s3.PostPolicyGenerator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.storage.VersionedProfile;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticProfilesManager;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticVersionedProfile;
import org.whispersystems.textsecuregcm.util.Pair;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.dropwizard.auth.Auth;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/profile")
public class ProfileController {

  private final Logger logger = LoggerFactory.getLogger(ProfileController.class);

  private final RateLimiters     rateLimiters;
  private final PossiblySyntheticProfilesManager profilesManager;
  private final PossiblySyntheticAccountsManager accountsManager;
  private final UsernamesManager usernamesManager;

  private final PolicySigner              policySigner;
  private final PostPolicyGenerator       policyGenerator;
  private final ServerZkProfileOperations zkProfileOperations;
  private final boolean                   isZkEnabled;

  private final AmazonS3            s3client;
  private final String              bucket;

  public ProfileController(RateLimiters rateLimiters,
                           PossiblySyntheticAccountsManager accountsManager,
                           PossiblySyntheticProfilesManager profilesManager,
                           UsernamesManager usernamesManager,
                           AmazonS3 s3client,
                           PostPolicyGenerator policyGenerator,
                           PolicySigner policySigner,
                           String bucket,
                           ServerZkProfileOperations zkProfileOperations,
                           boolean isZkEnabled)
  {
    this.rateLimiters        = rateLimiters;
    this.accountsManager     = accountsManager;
    this.profilesManager     = profilesManager;
    this.usernamesManager    = usernamesManager;
    this.zkProfileOperations = zkProfileOperations;
    this.bucket              = bucket;
    this.s3client            = s3client;
    this.policyGenerator     = policyGenerator;
    this.policySigner        = policySigner;
    this.isZkEnabled         = isZkEnabled;
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setProfile(@Auth Account account, @Valid CreateProfileRequest request) {
    if (!isZkEnabled) throw new WebApplicationException(Response.Status.NOT_FOUND);

    Optional<? extends PossiblySyntheticVersionedProfile> currentProfile = profilesManager.get(account.getUuid(), request.getVersion());
    String                                  avatar         = request.isAvatar() ? generateAvatarObjectName() : null;
    Optional<ProfileAvatarUploadAttributes> response       = Optional.empty();

    profilesManager.set(account.getUuid(), new VersionedProfile(request.getVersion(), request.getName(), avatar, request.getEmailAddress(), request.getCommitment().serialize()));

    if (request.isAvatar()) {
      Optional<String> currentAvatar = Optional.empty();

      if (currentProfile.isPresent() && currentProfile.get().getAvatar() != null && currentProfile.get().getAvatar().startsWith("profiles/")) {
        currentAvatar = Optional.of(currentProfile.get().getAvatar());
      }

      if (currentAvatar.isEmpty() && account.getAvatar() != null && account.getAvatar().startsWith("profiles/")) {
        currentAvatar = Optional.of(account.getAvatar());
      }

      currentAvatar.ifPresent(s -> s3client.deleteObject(bucket, s));

      response = Optional.of(generateAvatarUploadForm(avatar));
    }

    account.setProfileName(request.getName());
    account.setProfileEmailAddress(request.getEmailAddress());
    if (avatar != null) account.setAvatar(avatar);
    accountsManager.update(account);

    if (response.isPresent()) return Response.ok(response).build();
    else                      return Response.ok().build();
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{uuid}/{version}")
  public Optional<Profile> getProfile(@Auth                                     Account realRequestAccount,
                                      @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
                                      @PathParam("uuid")                        UUID uuid,
                                      @PathParam("version")                     String version)
      throws RateLimitExceededException
  {
    if (!isZkEnabled) throw new WebApplicationException(Response.Status.NOT_FOUND);
    // Unlike Signal, we expect every API to fully authenticate the real source, and edge routers are going to authenticate
    // way before it gets to the Java server. Those edge routers make it possible to stop denial of service.
    // However, it is perfectly fine if we treat the effective account as unknown from this point onwards; that will
    // force, among other things, a validation of the anonymous access key.
    Optional<Account> requestAccount = accessKey.isPresent() ? Optional.empty() : Optional.of(realRequestAccount);
    return getVersionedProfile(requestAccount, accessKey, uuid, version, Optional.empty());
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{uuid}/{version}/{credentialRequest}")
  public Optional<Profile> getProfile(@Auth                                     Account realRequestAccount,
                                      @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
                                      @PathParam("uuid")                        UUID uuid,
                                      @PathParam("version")                     String version,
                                      @PathParam("credentialRequest")           String credentialRequest)
      throws RateLimitExceededException
  {
    if (!isZkEnabled) throw new WebApplicationException(Response.Status.NOT_FOUND);
    // Unlike Signal, we expect every API to fully authenticate the real source, and edge routers are going to authenticate
    // way before it gets to the Java server. Those edge routers make it possible to stop denial of service.
    // However, it is perfectly fine if we treat the effective account as unknown from this point onwards; that will
    // force, among other things, a validation of the anonymous access key.
    Optional<Account> requestAccount = accessKey.isPresent() ? Optional.empty() : Optional.of(realRequestAccount);
    return getVersionedProfile(requestAccount, accessKey, uuid, version, Optional.of(credentialRequest));
  }

  @SuppressWarnings("OptionalIsPresent")
  private Optional<Profile> getVersionedProfile(Optional<Account> requestAccount,
                                                Optional<Anonymous> accessKey,
                                                UUID uuid,
                                                String version,
                                                Optional<String> credentialRequest)
      throws RateLimitExceededException
  {
    if (!isZkEnabled) throw new WebApplicationException(Response.Status.NOT_FOUND);

    try {
      if (!requestAccount.isPresent() && !accessKey.isPresent()) {
        throw new WebApplicationException(Response.Status.UNAUTHORIZED);
      }

      if (requestAccount.isPresent()) {
        rateLimiters.getProfileLimiter().validate(requestAccount.get().getNumber());
      }

      PossiblySyntheticAccount accountProfile = accountsManager.get(uuid);
      OptionalAccess.verify(requestAccount, accessKey, accountProfile);

      Optional<String>                                      username = usernamesManager.get(accountProfile.getUuid());
      Optional<? extends PossiblySyntheticVersionedProfile> profile  = profilesManager.get(uuid, version);

      String           name         = profile.map(PossiblySyntheticVersionedProfile::getName).orElse(accountProfile.getProfileName());
      String           emailAddress = profile.map(PossiblySyntheticVersionedProfile::getEmailAddress).orElse(accountProfile.getProfileEmailAddress());
      String           avatar       = profile.map(PossiblySyntheticVersionedProfile::getAvatar).orElse(accountProfile.getAvatar());

      Optional<ProfileKeyCredentialResponse> credential = getProfileCredential(credentialRequest, profile, uuid);

      return Optional.of(new Profile(name,
                                     avatar,
                                     accountProfile.getIdentityKey(),
                                     UnidentifiedAccessChecksum.generateFor(accountProfile.getUnidentifiedAccessKey()),
                                     accountProfile.isUnrestrictedUnidentifiedAccess(),
                                     new UserCapabilities(accountProfile.isUuidAddressingSupported(), accountProfile.isGroupsV2Supported()),
                                     username.orElse(null),
                                     null,
                                     emailAddress,
                                     credential.orElse(null)));
    } catch (InvalidInputException e) {
      logger.info("Bad profile request", e);
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
  }

  private Optional<ProfileKeyCredentialResponse> getProfileCredential(Optional<String>                                      encodedProfileCredentialRequest,
                                                                      Optional<? extends PossiblySyntheticVersionedProfile> profile,
                                                                      UUID                                                  uuid)
      throws InvalidInputException
  {
    if (!encodedProfileCredentialRequest.isPresent()) return Optional.empty();
    if (!profile.isPresent())                         return Optional.empty();

    try {
      ProfileKeyCommitment         commitment = new ProfileKeyCommitment(profile.get().getCommitment());
      ProfileKeyCredentialRequest  request    = new ProfileKeyCredentialRequest(Hex.decodeHex(encodedProfileCredentialRequest.get()));
      ProfileKeyCredentialResponse response   = zkProfileOperations.issueProfileKeyCredential(request, uuid, commitment);

      return Optional.of(response);
    } catch (DecoderException | VerificationFailedException e) {
      throw new WebApplicationException(e, Response.status(Response.Status.BAD_REQUEST).build());
    }
  }


  // Old profile endpoints. Replaced by versioned profile endpoints (above)

  // This method was marked deprecated, but the protocol requires that you can
  // download an encrypted profile without knowing the latest version
  //     WAS: @Deprecated
  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{identifier}")
  public Profile getProfile(@Auth                                     Account             realRequestAccount,
                            @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
                            @PathParam("identifier") AmbiguousIdentifier identifier,
                            @QueryParam("ca")                         boolean useCaCertificate)
          throws RateLimitExceededException
  {
    // Unlike Signal, we expect every API to fully authenticate the real source, and edge routers are going to authenticate
    // way before it gets to the Java server. Those edge routers make it possible to stop denial of service.
    // However, it is perfectly fine if we treat the effective account as unknown from this point onwards; that will
    // force, among other things, a validation of the anonymous access key.
    Optional<Account> requestAccount = accessKey.isPresent() ? Optional.empty() : Optional.of(realRequestAccount);

    if (!requestAccount.isPresent() && !accessKey.isPresent()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    if (!identifier.hasUuid()) {
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    if (requestAccount.isPresent()) {
      rateLimiters.getProfileLimiter().validate(requestAccount.get().getNumber());
    }

    PossiblySyntheticAccount accountProfile = accountsManager.get(identifier.getUuid());
    OptionalAccess.verify(requestAccount, accessKey, accountProfile);

    Optional<String> username = usernamesManager.get(accountProfile.getUuid());

    return new Profile(accountProfile.getProfileName(),
                       accountProfile.getAvatar(),
                       accountProfile.getIdentityKey(),
                       UnidentifiedAccessChecksum.generateFor(accountProfile.getUnidentifiedAccessKey()),
                       accountProfile.isUnrestrictedUnidentifiedAccess(),
                       new UserCapabilities(accountProfile.isUuidAddressingSupported(), accountProfile.isGroupsV2Supported()),
                       username.orElse(null),
                       null,
                       accountProfile.getProfileEmailAddress(),
                       null);
  }

  private ProfileAvatarUploadAttributes generateAvatarUploadForm(String objectName) {
    ZonedDateTime        now            = ZonedDateTime.now(ZoneOffset.UTC);
    Pair<String, String> policy         = policyGenerator.createFor(now, objectName, 10 * 1024 * 1024);
    String               signature      = policySigner.getSignature(now, policy.second());

    return new ProfileAvatarUploadAttributes(objectName, policy.first(), "private", "AWS4-HMAC-SHA256",
                                             now.format(PostPolicyGenerator.AWS_DATE_TIME), policy.second(), signature);

  }

  private String generateAvatarObjectName() {
    return generateAvatarObjectName(bytes -> new SecureRandom().nextBytes(bytes));
  }

  public static String generateAvatarObjectName(Consumer<byte[]> randomSetterOfBytes) {
    byte[] object = new byte[16];
    randomSetterOfBytes.accept(object);

    return "profiles/" + Base64.encodeBase64URLSafeString(object);
  }
}
