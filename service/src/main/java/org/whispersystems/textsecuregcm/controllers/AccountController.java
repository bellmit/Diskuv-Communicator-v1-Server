/*
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.annotation.Timed;
import com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.*;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.AccountCreationResult;
import org.whispersystems.textsecuregcm.entities.ApnRegistrationId;
import org.whispersystems.textsecuregcm.entities.DeprecatedPin;
import org.whispersystems.textsecuregcm.entities.DeviceName;
import org.whispersystems.textsecuregcm.entities.GcmRegistrationId;
import org.whispersystems.textsecuregcm.entities.RegistrationLock;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.push.APNSender;
import org.whispersystems.textsecuregcm.push.ApnMessage;
import org.whispersystems.textsecuregcm.push.GCMSender;
import org.whispersystems.textsecuregcm.push.GcmMessage;
import org.whispersystems.textsecuregcm.recaptcha.RecaptchaClient;
import org.whispersystems.textsecuregcm.sms.SmsSender;
import org.whispersystems.textsecuregcm.storage.AbusiveHostRule;
import org.whispersystems.textsecuregcm.storage.AbusiveHostRules;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.PendingAccountsManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.util.ByteUtil;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.DiskuvUuidType;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import org.whispersystems.textsecuregcm.util.Hex;
import org.whispersystems.textsecuregcm.util.Util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;
import static com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER;

import io.dropwizard.auth.Auth;

/**
 * Unlike the original Signal REST endpoints, this controller drops the phone number parameters. It instead
 * expects a JWT token with a verifiable claim about the email address. [Diskuv Change] [class]
 *
 * Also the preAuth sequence is different, and we've renamed it to preReg to reflect the difference. Since
 * we already have a verifiable JWT identity claim, we don't need an SMS code verification. However, we still
 * want to verify any FCM/APN channel before we blindly accept the device FCM/APN message tokens from the user. So
 * before an account is registered (ie. preReg), we still validate the device verification code.
 *
 * Just as Signal did, you are _not_ required to use FCM/APN.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/accounts")
public class AccountController {

  private final Logger         logger                 = LoggerFactory.getLogger(AccountController.class);
  private final MetricRegistry metricRegistry         = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          newUserMeter           = metricRegistry.meter(name(AccountController.class, "brand_new_user"     ));
  private final Meter          blockedHostMeter       = metricRegistry.meter(name(AccountController.class, "blocked_host"       ));
  private final Meter          filteredHostMeter      = metricRegistry.meter(name(AccountController.class, "filtered_host"      ));
  private final Meter          rateLimitedHostMeter   = metricRegistry.meter(name(AccountController.class, "rate_limited_host"  ));
  private final Meter          rateLimitedPrefixMeter = metricRegistry.meter(name(AccountController.class, "rate_limited_prefix"));
  private final Meter          captchaSuccessMeter    = metricRegistry.meter(name(AccountController.class, "captcha_success"    ));
  private final Meter          captchaFailureMeter    = metricRegistry.meter(name(AccountController.class, "captcha_failure"    ));


  private final AccountsManager                    accounts;
  private final JwtAuthentication jwtAuthentication;
  private final UsernamesManager                   usernames;
  private final AbusiveHostRules                   abusiveHostRules;
  private final RateLimiters                       rateLimiters;
  private final SmsSender                          smsSender;
  private final MessagesManager                    messagesManager;
  private final TurnTokenGenerator                 turnTokenGenerator;
  private final Map<String, Integer>               testDevices;
  private final RecaptchaClient                    recaptchaClient;
  private final GCMSender                          gcmSender;
  private final APNSender                          apnSender;
  private final ExternalServiceCredentialGenerator backupServiceCredentialGenerator;
  private final PendingAccountsManager             pendingAccounts;

  public AccountController(PendingAccountsManager pendingAccounts,
                           AccountsManager accounts,
                           JwtAuthentication jwtAuthentication,
                           UsernamesManager usernames,
                           AbusiveHostRules abusiveHostRules,
                           RateLimiters rateLimiters,
                           SmsSender smsSenderFactory,
                           MessagesManager messagesManager,
                           TurnTokenGenerator turnTokenGenerator,
                           Map<String, Integer> testDevices,
                           RecaptchaClient recaptchaClient,
                           GCMSender gcmSender,
                           APNSender apnSender,
                           ExternalServiceCredentialGenerator backupServiceCredentialGenerator)
  {
    this.pendingAccounts                   = pendingAccounts;
    this.accounts                          = accounts;
    this.jwtAuthentication                 = jwtAuthentication;
    this.usernames                         = usernames;
    this.abusiveHostRules                  = abusiveHostRules;
    this.rateLimiters                      = rateLimiters;
    this.smsSender                         = smsSenderFactory;
    this.messagesManager                   = messagesManager;
    this.testDevices                       = testDevices;
    this.turnTokenGenerator                = turnTokenGenerator;
    this.recaptchaClient                   = recaptchaClient;
    this.gcmSender                         = gcmSender;
    this.apnSender                         = apnSender;
    this.backupServiceCredentialGenerator  = backupServiceCredentialGenerator;
  }

  @Timed
  @GET
  @Path("/{type}/prereg/{accountId}/{token}")
  public Response getPreReg(
      @HeaderParam("Authorization") String authorizationHeader,
      @PathParam("type") String pushType,
      @PathParam("accountId") String accountId,
      @PathParam("token") String pushToken) {
    if (!"apn".equals(pushType) && !"fcm".equals(pushType)) {
      return Response.status(400).build();
    }

    try {
      DiskuvUuidUtil.verifyDiskuvUuid(accountId);
    } catch (IllegalArgumentException e) {
      return Response.status(400).build();
    }
    UUID accountUuid = UUID.fromString(accountId);

    AuthHeaderSupport.validateJwtAndGetOutdoorsUUID(jwtAuthentication, authorizationHeader);

    String                 pushChallenge          = generatePushChallenge(accountUuid, pushToken);
    StoredVerificationCode storedVerificationCode = new StoredVerificationCode(null,
            System.currentTimeMillis(),
            pushChallenge);

    pendingAccounts.store(accountUuid, storedVerificationCode);

    if ("fcm".equals(pushType)) {
      gcmSender.sendMessage(new GcmMessage(pushToken, accountId, 0, GcmMessage.Type.CHALLENGE, Optional.of(storedVerificationCode.getPushCode())));
    } else if ("apn".equals(pushType)) {
      apnSender.sendMessage(new ApnMessage(pushToken, accountId, 0, true, Optional.of(storedVerificationCode.getPushCode())));
    } else {
      throw new AssertionError();
    }

    return Response.ok().build();
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/account")
  public AccountCreationResult registerAccount(@HeaderParam("Authorization")   String authorizationHeader,
                                               @HeaderParam(DEVICE_AUTHORIZATION_HEADER)   String deviceAuthorizationHeader,
                                               @HeaderParam("X-Signal-Agent")  String userAgent,
                                               @HeaderParam("X-Forwarded-For") String forwardedFor,
                                               @QueryParam("challenge")        Optional<String> pushChallenge,
                                               @QueryParam("captcha")          Optional<String> captcha,
                                               @Valid                          AccountAttributes accountAttributes)
      throws RateLimitExceededException
  {
    // account authentication
    UUID outdoorsUUID = AuthHeaderSupport.validateJwtAndGetOutdoorsUUID(jwtAuthentication, authorizationHeader);

    // device password to be used for subsequent device authentication
    final DeviceAuthorizationHeader deviceHeader;
    try {
      deviceHeader = DeviceAuthorizationHeader.fromFullHeader(deviceAuthorizationHeader);
    } catch (InvalidAuthorizationHeaderException e) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    byte[] devicePassword = deviceHeader.getDevicePassword();
    UUID accountUuid = deviceHeader.getAccountId();
    String accountId = accountUuid.toString();

    // validate UUID if Outdoors (which anybody with knowledge of the email address can reconstruct)
    DiskuvUuidType diskuvUuidType;
    try {
      diskuvUuidType = DiskuvUuidUtil.verifyDiskuvUuid(accountId);
    } catch (IllegalArgumentException e) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    if (diskuvUuidType == DiskuvUuidType.OUTDOORS && !outdoorsUUID.equals(accountUuid)) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    String requester = Arrays.stream(forwardedFor.split(","))
            .map(String::trim)
            .reduce((a, b) -> b)
            .orElseThrow();

    Optional<StoredVerificationCode> storedChallenge = pendingAccounts.getCodeForPendingAccount(accountUuid);
    CaptchaRequirement               requirement     = requiresCaptcha(accountId, forwardedFor, requester, captcha, storedChallenge, pushChallenge);

    if (requirement.isCaptchaRequired()) {
      if (requirement.isAutoBlock() && shouldAutoBlock(requester)) {
        logger.info("Auto-block: " + requester);
        abusiveHostRules.setBlockedHost(requester, "Auto-Block");
      }

      throw new WebApplicationException(402);
    }

    rateLimiters.getVerifyLimiter().validate(accountId);

    Optional<Account>                    existingAccount           = accounts.get(accountUuid);

    Account account = createAccount(accountUuid, devicePassword, userAgent, accountAttributes);

    metricRegistry.meter(name(AccountController.class, "create")).mark();

    return new AccountCreationResult(account.getUuid(), existingAccount.map(Account::isStorageSupported).orElse(false));
  }

  @Timed
  @GET
  @Path("/turn/")
  @Produces(MediaType.APPLICATION_JSON)
  public TurnToken getTurnToken(@Auth Account account) throws RateLimitExceededException {
    rateLimiters.getTurnLimiter().validate(account.getNumber());
    return turnTokenGenerator.generate();
  }

  @Timed
  @PUT
  @Path("/gcm/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setGcmRegistrationId(@Auth DisabledPermittedAccount disabledPermittedAccount, @Valid GcmRegistrationId registrationId) {
    Account account           = disabledPermittedAccount.getAccount();
    Device  device            = account.getAuthenticatedDevice().get();
    boolean wasAccountEnabled = account.isEnabled();

    if (device.getGcmId() != null &&
        device.getGcmId().equals(registrationId.getGcmRegistrationId()))
    {
      return;
    }

    device.setApnId(null);
    device.setVoipApnId(null);
    device.setGcmId(registrationId.getGcmRegistrationId());
    device.setFetchesMessages(false);

    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/gcm/")
  public void deleteGcmRegistrationId(@Auth DisabledPermittedAccount disabledPermittedAccount) {
    Account account = disabledPermittedAccount.getAccount();
    Device  device  = account.getAuthenticatedDevice().get();
    device.setGcmId(null);
    device.setFetchesMessages(false);

    accounts.update(account);
  }

  @Timed
  @PUT
  @Path("/apn/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setApnRegistrationId(@Auth DisabledPermittedAccount disabledPermittedAccount, @Valid ApnRegistrationId registrationId) {
    Account account           = disabledPermittedAccount.getAccount();
    Device  device            = account.getAuthenticatedDevice().get();

    device.setApnId(registrationId.getApnRegistrationId());
    device.setVoipApnId(registrationId.getVoipRegistrationId());
    device.setGcmId(null);
    device.setFetchesMessages(false);
    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/apn/")
  public void deleteApnRegistrationId(@Auth DisabledPermittedAccount disabledPermittedAccount) {
    Account account = disabledPermittedAccount.getAccount();
    Device  device  = account.getAuthenticatedDevice().get();
    device.setApnId(null);
    device.setFetchesMessages(false);

    accounts.update(account);
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/registration_lock")
  public void setRegistrationLock(@Auth Account account, @Valid RegistrationLock accountLock) {
    AuthenticationCredentials credentials = new AuthenticationCredentials(accountLock.getRegistrationLock());
    account.setRegistrationLock(credentials.getHashedAuthenticationToken(), credentials.getSalt());
    account.setPin(null);

    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/registration_lock")
  public void removeRegistrationLock(@Auth Account account) {
    account.setRegistrationLock(null, null);
    accounts.update(account);
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/pin/")
  public void setPin(@Auth Account account, @Valid DeprecatedPin accountLock) {
    account.setPin(accountLock.getPin());
    account.setRegistrationLock(null, null);

    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/pin/")
  public void removePin(@Auth Account account) {
    account.setPin(null);
    accounts.update(account);
  }

  @Timed
  @PUT
  @Path("/name/")
  public void setName(@Auth DisabledPermittedAccount disabledPermittedAccount, @Valid DeviceName deviceName) {
    Account account = disabledPermittedAccount.getAccount();
    account.getAuthenticatedDevice().get().setName(deviceName.getDeviceName());
    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/signaling_key")
  public void removeSignalingKey(@Auth DisabledPermittedAccount disabledPermittedAccount) {
    Account account = disabledPermittedAccount.getAccount();
    account.getAuthenticatedDevice().get().setSignalingKey(null);
    accounts.update(account);
  }

  @Timed
  @PUT
  @Path("/attributes/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setAccountAttributes(@Auth DisabledPermittedAccount disabledPermittedAccount,
                                   @HeaderParam("X-Signal-Agent") String userAgent,
                                   @Valid AccountAttributes attributes)
  {
    Account account = disabledPermittedAccount.getAccount();
    Device  device  = account.getAuthenticatedDevice().get();

    device.setFetchesMessages(attributes.getFetchesMessages());
    device.setName(attributes.getName());
    device.setLastSeen(Util.todayInMillis());
    device.setCapabilities(attributes.getCapabilities());
    device.setRegistrationId(attributes.getRegistrationId());
    device.setSignalingKey(attributes.getSignalingKey());
    device.setUserAgent(userAgent);

    setAccountRegistrationLockFromAttributes(account, attributes);

    account.setUnidentifiedAccessKey(attributes.getUnidentifiedAccessKey());
    account.setUnrestrictedUnidentifiedAccess(attributes.isUnrestrictedUnidentifiedAccess());

    accounts.update(account);
  }
  @GET
  @Path("/whoami")
  @Produces(MediaType.APPLICATION_JSON)
  public AccountCreationResult whoAmI(@Auth Account account) {
    return new AccountCreationResult(account.getUuid(), account.isStorageSupported());
  }

  @DELETE
  @Path("/username")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteUsername(@Auth Account account) {
    usernames.delete(account.getUuid());
  }

  @PUT
  @Path("/username/{username}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response setUsername(@Auth Account account, @PathParam("username") String username) throws RateLimitExceededException {
    rateLimiters.getUsernameSetLimiter().validate(account.getUuid().toString());

    if (username == null || username.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    username = username.toLowerCase();

    if (!username.matches("^[a-z_][a-z0-9_]+$")) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    if (!usernames.put(account.getUuid(), username)) {
      return Response.status(Response.Status.CONFLICT).build();
    }

    return Response.ok().build();
  }

  private CaptchaRequirement requiresCaptcha(String accountId,
                                             String forwardedFor,
                                             String requester,
                                             Optional<String>                 captchaToken,
                                             Optional<StoredVerificationCode> storedVerificationCode,
                                             Optional<String>                 pushChallenge)
  {

    if (captchaToken.isPresent()) {
      boolean validToken = recaptchaClient.verify(captchaToken.get(), requester);

      if (validToken) {
        captchaSuccessMeter.mark();
        return new CaptchaRequirement(false, false);
      } else {
        captchaFailureMeter.mark();
        return new CaptchaRequirement(true, false);
      }
    }

    if (pushChallenge.isPresent()) {
      Optional<String> storedPushChallenge = storedVerificationCode.map(StoredVerificationCode::getPushCode);

      if (!pushChallenge.get().equals(storedPushChallenge.orElse(null))) {
        return new CaptchaRequirement(true, false);
      }
    }

    // We don't have to carry many of Signal's abuse rules since additional
    // abuse checks will be done at login time
    List<AbusiveHostRule> abuseRules = abusiveHostRules.getAbusiveHostRulesFor(requester);

    for (AbusiveHostRule abuseRule : abuseRules) {
      if (abuseRule.isBlocked()) {
        logger.info("Blocked host: " + accountId + ", " + requester + " (" + forwardedFor + ")");
        blockedHostMeter.mark();
        return new CaptchaRequirement(true, false);
      }
    }

    try {
      rateLimiters.getSmsVoiceIpLimiter().validate(requester);
    } catch (RateLimitExceededException e) {
      logger.info("Rate limited exceeded: " + accountId + ", " + requester + " (" + forwardedFor + ")");
      rateLimitedHostMeter.mark();
      return new CaptchaRequirement(true, true);
    }

    return new CaptchaRequirement(false, false);
  }

  private boolean shouldAutoBlock(String requester) {
    try {
      rateLimiters.getAutoBlockLimiter().validate(requester);
    } catch (RateLimitExceededException e) {
      return true;
    }

    return false;
  }

  private Account createAccount(UUID accountUuid, byte[] devicePassword, String userAgent, AccountAttributes accountAttributes) {
    Device device = new Device();
    device.setId(Device.MASTER_ID);
    device.setAuthenticationCredentials(new AuthenticationCredentials(devicePassword));
    device.setSignalingKey(accountAttributes.getSignalingKey());
    device.setFetchesMessages(accountAttributes.getFetchesMessages());
    device.setRegistrationId(accountAttributes.getRegistrationId());
    device.setName(accountAttributes.getName());
    device.setCapabilities(accountAttributes.getCapabilities());
    device.setCreated(System.currentTimeMillis());
    device.setLastSeen(Util.todayInMillis());
    device.setUserAgent(userAgent);

    Account account = new Account();
    // Login by email. So no phone numbers. WAS: account.setNumber(number);
    account.setNumber("");
    account.setUuid(accountUuid);
    account.addDevice(device);
    setAccountRegistrationLockFromAttributes(account, accountAttributes);
    account.setUnidentifiedAccessKey(accountAttributes.getUnidentifiedAccessKey());
    account.setUnrestrictedUnidentifiedAccess(accountAttributes.isUnrestrictedUnidentifiedAccess());

    if (accounts.create(account)) {
      newUserMeter.mark();
    }

    messagesManager.clear(accountUuid.toString());

    return account;
  }

  private void setAccountRegistrationLockFromAttributes(Account account, @Valid AccountAttributes attributes) {
    if (!Util.isEmpty(attributes.getPin())) {
      account.setPin(attributes.getPin());
    } else if (!Util.isEmpty(attributes.getRegistrationLock())) {
      AuthenticationCredentials credentials = new AuthenticationCredentials(attributes.getRegistrationLock());
      account.setRegistrationLock(credentials.getHashedAuthenticationToken(), credentials.getSalt());
    } else {
      account.setPin(null);
      account.setRegistrationLock(null, null);
    }
  }

  private String generatePushChallenge(UUID accountUuid, String pushToken) {
    // Docs: 2021-05-01-authenticated-push-challenge.md

    // NONCE = RANDOM_BYTES(16)
    SecureRandom random    = new SecureRandom();
    byte[]       nonce = new byte[16];
    random.nextBytes(nonce);

    // SECRET = UTF8_BYTES(PUSH_TOKEN || ACCOUNT_UUID)
    byte[] secret = (pushToken + accountUuid).getBytes(StandardCharsets.UTF_8);

    // PUSH_CHALLENGE = HEX(NONCE || HMAC_SHA256(SECRET, NONCE))
    Mac mac;
    try {
      mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new WebApplicationException(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
    }
    byte[] digest = mac.doFinal(nonce);
    return Hex.toStringCondensed(ByteUtil.combine(nonce, digest));
  }

  private static class CaptchaRequirement {
    private final boolean captchaRequired;
    private final boolean autoBlock;

    private CaptchaRequirement(boolean captchaRequired, boolean autoBlock) {
      this.captchaRequired = captchaRequired;
      this.autoBlock = autoBlock;
    }

    boolean isCaptchaRequired() {
      return captchaRequired;
    }

    boolean isAutoBlock() {
      return autoBlock;
    }
  }
}
