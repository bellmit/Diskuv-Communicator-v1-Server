package org.whispersystems.textsecuregcm.tests.controllers;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.apache.commons.codec.DecoderException;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentialGenerator;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import org.whispersystems.textsecuregcm.auth.StoredRegistrationLock;
import org.whispersystems.textsecuregcm.auth.TurnTokenGenerator;
import org.whispersystems.textsecuregcm.controllers.AccountController;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.AccountCreationResult;
import org.whispersystems.textsecuregcm.entities.ApnRegistrationId;
import org.whispersystems.textsecuregcm.entities.DeprecatedPin;
import org.whispersystems.textsecuregcm.entities.GcmRegistrationId;
import org.whispersystems.textsecuregcm.entities.RegistrationLock;
import org.whispersystems.textsecuregcm.entities.RegistrationLockFailure;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.mappers.RateLimitExceededExceptionMapper;
import org.whispersystems.textsecuregcm.providers.TimeProvider;
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
import org.whispersystems.textsecuregcm.storage.PaymentAddress;
import org.whispersystems.textsecuregcm.storage.PaymentAddressList;
import org.whispersystems.textsecuregcm.storage.PendingAccountsManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;
import org.whispersystems.textsecuregcm.util.Hex;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class AccountControllerTest {

  private static final String SENDER             = "+14152222222";
  private static final String SENDER_OLD         = "+14151111111";
  private static final String SENDER_PIN         = "+14153333333";
  private static final String SENDER_OVER_PIN    = "+14154444444";
  private static final String SENDER_OVER_PREFIX = "+14156666666";
  private static final String SENDER_PREAUTH     = "+14157777777";
  private static final String SENDER_REG_LOCK    = "+14158888888";
  private static final String SENDER_HAS_STORAGE = "+14159999999";
  private static final String SENDER_TRANSFER    = "+14151111112";

  private static final UUID   SENDER_REG_LOCK_UUID = UUID.randomUUID();

  private static final String ABUSIVE_HOST             = "192.168.1.1";
  private static final String RESTRICTED_HOST          = "192.168.1.2";
  private static final String NICE_HOST                = "127.0.0.1";
  private static final String RATE_LIMITED_IP_HOST     = "10.0.0.1";
  private static final String RATE_LIMITED_PREFIX_HOST = "10.0.0.2";
  private static final String RATE_LIMITED_HOST2       = "10.0.0.3";

  private static final String VALID_CAPTCHA_TOKEN   = "valid_token";
  private static final String INVALID_CAPTCHA_TOKEN = "invalid_token";

  private        PendingAccountsManager pendingAccountsManager = mock(PendingAccountsManager.class);
  private        AccountsManager        accountsManager        = mock(AccountsManager.class       );
  private        JwtAuthentication      jwtAuthentication      = mock(JwtAuthentication.class     );
  private        AbusiveHostRules       abusiveHostRules       = mock(AbusiveHostRules.class      );
  private        RateLimiters           rateLimiters           = mock(RateLimiters.class          );
  private        RateLimiter            rateLimiter            = mock(RateLimiter.class           );
  private        RateLimiter            pinLimiter             = mock(RateLimiter.class           );
  private        RateLimiter            smsVoiceIpLimiter      = mock(RateLimiter.class           );
  private        RateLimiter            smsVoicePrefixLimiter  = mock(RateLimiter.class);
  private        RateLimiter            autoBlockLimiter       = mock(RateLimiter.class);
  private        RateLimiter            usernameSetLimiter     = mock(RateLimiter.class);
  private        SmsSender              smsSender              = mock(SmsSender.class             );
  private        MessagesManager        storedMessages         = mock(MessagesManager.class       );
  private        TimeProvider           timeProvider           = mock(TimeProvider.class          );
  private        TurnTokenGenerator     turnTokenGenerator     = mock(TurnTokenGenerator.class);
  private        Account                senderPinAccount       = mock(Account.class);
  private        Account                senderRegLockAccount   = mock(Account.class);
  private        Account                senderHasStorage       = mock(Account.class);
  private        Account                senderTransfer         = mock(Account.class);
  private        RecaptchaClient        recaptchaClient        = mock(RecaptchaClient.class);
  private        GCMSender              gcmSender              = mock(GCMSender.class);
  private        APNSender              apnSender              = mock(APNSender.class);
  private UsernamesManager              usernamesManager       = mock(UsernamesManager.class);

  private byte[] registration_lock_key = new byte[32];
  private ExternalServiceCredentialGenerator storageCredentialGenerator = new ExternalServiceCredentialGenerator(new byte[32], new byte[32], false);

  @Rule
  public final ResourceTestRule resources = ResourceTestRule.builder()
                                                            .addProvider(AuthHelper.getAuthFilter())
                                                            .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                                                            .addProvider(new RateLimitExceededExceptionMapper())
                                                            .setMapper(SystemMapper.getMapper())
                                                            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                                                            .addResource(new AccountController(pendingAccountsManager,
                                                                                               accountsManager,
                                                                                               jwtAuthentication,
                                                                                               usernamesManager,
                                                                                               abusiveHostRules,
                                                                                               rateLimiters,
                                                                                               smsSender,
                                                                    storedMessages,
                                                                                               turnTokenGenerator,
                                                                                               new HashMap<>(),
                                                                                               recaptchaClient,
                                                                                               gcmSender,
                                                                                               apnSender,
                                                                                               storageCredentialGenerator))
                                                            .build();


  @Before
  public void setup() throws Exception {
    new SecureRandom().nextBytes(registration_lock_key);
    AuthenticationCredentials registrationLockCredentials = new AuthenticationCredentials(Hex.toStringCondensed(registration_lock_key));

    when(rateLimiters.getSmsDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVoiceDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVerifyLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getPinLimiter()).thenReturn(pinLimiter);
    when(rateLimiters.getSmsVoiceIpLimiter()).thenReturn(smsVoiceIpLimiter);
    when(rateLimiters.getSmsVoicePrefixLimiter()).thenReturn(smsVoicePrefixLimiter);
    when(rateLimiters.getAutoBlockLimiter()).thenReturn(autoBlockLimiter);
    when(rateLimiters.getUsernameSetLimiter()).thenReturn(usernameSetLimiter);

    when(timeProvider.getCurrentTimeMillis()).thenReturn(System.currentTimeMillis());

    when(senderPinAccount.getLastSeen()).thenReturn(System.currentTimeMillis());
    when(senderPinAccount.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), Optional.of("31337"), System.currentTimeMillis()));

    when(senderHasStorage.getUuid()).thenReturn(UUID.randomUUID());
    when(senderHasStorage.isStorageSupported()).thenReturn(true);
    when(senderHasStorage.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), Optional.empty(), System.currentTimeMillis()));

    when(senderRegLockAccount.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.of(registrationLockCredentials.getHashedAuthenticationToken()), Optional.of(registrationLockCredentials.getSalt()), Optional.empty(), System.currentTimeMillis()));
    when(senderRegLockAccount.getLastSeen()).thenReturn(System.currentTimeMillis());
    when(senderRegLockAccount.getUuid()).thenReturn(SENDER_REG_LOCK_UUID);

    /*
    when(pendingAccountsManager.getCodeForNumber(SENDER)).thenReturn(Optional.of(new StoredVerificationCode("1234", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_OLD)).thenReturn(Optional.of(new StoredVerificationCode("1234", System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(31), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_PIN)).thenReturn(Optional.of(new StoredVerificationCode("333333", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_REG_LOCK)).thenReturn(Optional.of(new StoredVerificationCode("666666", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_OVER_PIN)).thenReturn(Optional.of(new StoredVerificationCode("444444", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_PREAUTH)).thenReturn(Optional.of(new StoredVerificationCode("555555", System.currentTimeMillis(), "validchallenge")));
    when(pendingAccountsManager.getCodeForNumber(SENDER_HAS_STORAGE)).thenReturn(Optional.of(new StoredVerificationCode("666666", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_TRANSFER)).thenReturn(Optional.of(new StoredVerificationCode("1234", System.currentTimeMillis(), null)));
    */

    when(accountsManager.get(eq(SENDER_PIN))).thenReturn(Optional.of(senderPinAccount));
    when(accountsManager.get(eq(SENDER_REG_LOCK))).thenReturn(Optional.of(senderRegLockAccount));
    when(accountsManager.get(eq(SENDER_OVER_PIN))).thenReturn(Optional.of(senderPinAccount));
    when(accountsManager.get(eq(SENDER))).thenReturn(Optional.empty());
    when(accountsManager.get(eq(SENDER_OLD))).thenReturn(Optional.empty());
    when(accountsManager.get(eq(SENDER_PREAUTH))).thenReturn(Optional.empty());
    when(accountsManager.get(eq(SENDER_HAS_STORAGE))).thenReturn(Optional.of(senderHasStorage));
    when(accountsManager.get(eq(SENDER_TRANSFER))).thenReturn(Optional.of(senderTransfer));

    when(usernamesManager.put(eq(AuthHelper.VALID_UUID), eq("n00bkiller"))).thenReturn(true);
    when(usernamesManager.put(eq(AuthHelper.VALID_UUID), eq("takenusername"))).thenReturn(false);

    when(abusiveHostRules.getAbusiveHostRulesFor(eq(ABUSIVE_HOST))).thenReturn(Collections.singletonList(new AbusiveHostRule(ABUSIVE_HOST, true, Collections.emptyList())));
    when(abusiveHostRules.getAbusiveHostRulesFor(eq(RESTRICTED_HOST))).thenReturn(Collections.singletonList(new AbusiveHostRule(RESTRICTED_HOST, false, Collections.singletonList("+123"))));
    when(abusiveHostRules.getAbusiveHostRulesFor(eq(NICE_HOST))).thenReturn(Collections.emptyList());

    when(recaptchaClient.verify(eq(INVALID_CAPTCHA_TOKEN), anyString())).thenReturn(false);
    when(recaptchaClient.verify(eq(VALID_CAPTCHA_TOKEN), anyString())).thenReturn(true);

    when(jwtAuthentication.verifyBearerTokenAndGetEmailAddress(AuthHelper.VALID_BEARER_TOKEN)).thenReturn(AuthHelper.VALID_EMAIL);

    doThrow(new RateLimitExceededException(SENDER_OVER_PIN)).when(pinLimiter).validate(eq(SENDER_OVER_PIN));

    doThrow(new RateLimitExceededException(RATE_LIMITED_PREFIX_HOST)).when(autoBlockLimiter).validate(eq(RATE_LIMITED_PREFIX_HOST));
    doThrow(new RateLimitExceededException(RATE_LIMITED_IP_HOST)).when(autoBlockLimiter).validate(eq(RATE_LIMITED_IP_HOST));

    doThrow(new RateLimitExceededException(SENDER_OVER_PREFIX)).when(smsVoicePrefixLimiter).validate(SENDER_OVER_PREFIX.substring(0, 4+2));
    doThrow(new RateLimitExceededException(RATE_LIMITED_IP_HOST)).when(smsVoiceIpLimiter).validate(RATE_LIMITED_IP_HOST);
    doThrow(new RateLimitExceededException(RATE_LIMITED_HOST2)).when(smsVoiceIpLimiter).validate(RATE_LIMITED_HOST2);
  }

  @Test
  public void testGetFcmPrereg() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/accounts/fcm/prereg/" + org.whispersystems.textsecuregcm.tests.util.AuthHelper.VALID_UUID + "/mytoken")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<GcmMessage> captor = ArgumentCaptor.forClass(GcmMessage.class);

    verify(gcmSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getGcmId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getData().isPresent()).isTrue();
    // Hex (2*). Nonce = 16. HMAC-SHA256=256/8.
    assertThat(captor.getValue().getData().get().length()).isEqualTo(2 * (16 + 256/8));
    validatePushChallengeAuthentication(captor.getValue().getData().get(), "mytoken", VALID_UUID);

    verifyNoMoreInteractions(apnSender);
  }

  @Test
  public void testGetApnPrereg() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/accounts/apn/prereg/"+org.whispersystems.textsecuregcm.tests.util.AuthHelper.VALID_UUID+"/mytoken")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ApnMessage> captor = ArgumentCaptor.forClass(ApnMessage.class);

    verify(apnSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getApnId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getChallengeData().isPresent()).isTrue();
    // Hex (2*). Nonce = 16. HMAC-SHA256=256/8.
    assertThat(captor.getValue().getChallengeData().get().length()).isEqualTo(2 * (16 + 256/8));
    assertThat(captor.getValue().getMessage()).contains("\"challenge\" : \"" + captor.getValue().getChallengeData().get() + "\"");
    validatePushChallengeAuthentication(captor.getValue().getChallengeData().get(), "mytoken", VALID_UUID);


    verifyNoMoreInteractions(gcmSender);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendCode() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .request()
                 .header("X-Forwarded-For", NICE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verify(smsSender).deliverSmsVerification(eq(SENDER), eq(Optional.empty()), anyString());
    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(NICE_HOST));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendCodeWithValidPreauth() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER_PREAUTH))
                 .queryParam("challenge", "validchallenge")
                 .request()
                 .header("X-Forwarded-For", NICE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verify(smsSender).deliverSmsVerification(eq(SENDER_PREAUTH), eq(Optional.empty()), anyString());
    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(NICE_HOST));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendCodeWithInvalidPreauth() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER_PREAUTH))
                 .queryParam("challenge", "invalidchallenge")
                 .request()
                 .header("X-Forwarded-For", NICE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verifyNoMoreInteractions(smsSender);
    verifyNoMoreInteractions(abusiveHostRules);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendCodeWithNoPreauth() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER_PREAUTH))
                 .request()
                 .header("X-Forwarded-For", NICE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verify(smsSender).deliverSmsVerification(eq(SENDER_PREAUTH), eq(Optional.empty()), anyString());
    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(NICE_HOST));
  }


  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendiOSCode() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .queryParam("client", "ios")
                 .request()
                 .header("X-Forwarded-For", NICE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verify(smsSender).deliverSmsVerification(eq(SENDER), eq(Optional.of("ios")), anyString());
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendAndroidNgCode() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .queryParam("client", "android-ng")
                 .request()
                 .header("X-Forwarded-For", NICE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verify(smsSender).deliverSmsVerification(eq(SENDER), eq(Optional.of("android-ng")), anyString());
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendAbusiveHost() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .request()
                 .header("X-Forwarded-For", ABUSIVE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(ABUSIVE_HOST));
    verifyNoMoreInteractions(smsSender);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendAbusiveHostWithValidCaptcha() throws IOException {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .queryParam("captcha", VALID_CAPTCHA_TOKEN)
                 .request()
                 .header("X-Forwarded-For", ABUSIVE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verifyNoMoreInteractions(abusiveHostRules);
    verify(recaptchaClient).verify(eq(VALID_CAPTCHA_TOKEN), eq(ABUSIVE_HOST));
    verify(smsSender).deliverSmsVerification(eq(SENDER), eq(Optional.empty()), anyString());
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendAbusiveHostWithInvalidCaptcha() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .queryParam("captcha", INVALID_CAPTCHA_TOKEN)
                 .request()
                 .header("X-Forwarded-For", ABUSIVE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verifyNoMoreInteractions(abusiveHostRules);
    verify(recaptchaClient).verify(eq(INVALID_CAPTCHA_TOKEN), eq(ABUSIVE_HOST));
    verifyNoMoreInteractions(smsSender);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendRateLimitedHostAutoBlock() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .request()
                 .header("X-Forwarded-For", RATE_LIMITED_IP_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(RATE_LIMITED_IP_HOST));
    verify(abusiveHostRules).setBlockedHost(eq(RATE_LIMITED_IP_HOST), eq("Auto-Block"));
    verifyNoMoreInteractions(abusiveHostRules);

    verifyNoMoreInteractions(recaptchaClient);
    verifyNoMoreInteractions(smsSender);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendRateLimitedPrefixAutoBlock() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER_OVER_PREFIX))
                 .request()
                 .header("X-Forwarded-For", RATE_LIMITED_PREFIX_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(RATE_LIMITED_PREFIX_HOST));
    verify(abusiveHostRules).setBlockedHost(eq(RATE_LIMITED_PREFIX_HOST), eq("Auto-Block"));
    verifyNoMoreInteractions(abusiveHostRules);

    verifyNoMoreInteractions(recaptchaClient);
    verifyNoMoreInteractions(smsSender);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendRateLimitedHostNoAutoBlock() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .request()
                 .header("X-Forwarded-For", RATE_LIMITED_HOST2)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(RATE_LIMITED_HOST2));
    verifyNoMoreInteractions(abusiveHostRules);

    verifyNoMoreInteractions(recaptchaClient);
    verifyNoMoreInteractions(smsSender);
  }


  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendMultipleHost() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .request()
                 .header("X-Forwarded-For", NICE_HOST + ", " + ABUSIVE_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verify(abusiveHostRules, times(1)).getAbusiveHostRulesFor(eq(ABUSIVE_HOST));

    verifyNoMoreInteractions(abusiveHostRules);
    verifyNoMoreInteractions(smsSender);
  }


  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendRestrictedHostOut() {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", SENDER))
                 .request()
                 .header("X-Forwarded-For", RESTRICTED_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(402);

    verify(abusiveHostRules).getAbusiveHostRulesFor(eq(RESTRICTED_HOST));
    verifyNoMoreInteractions(smsSender);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testSendRestrictedIn() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/sms/code/%s", "+12345678901"))
                 .request()
                 .header("X-Forwarded-For", RESTRICTED_HOST)
                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    verify(smsSender).deliverSmsVerification(eq("+12345678901"), eq(Optional.empty()), anyString());
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyCode() throws Exception {
    AccountCreationResult result =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "1234"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 2222, null),
                               MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

    assertThat(result.getUuid()).isNotNull();
    assertThat(result.isStorageCapable()).isFalse();

    verify(accountsManager, times(1)).create(isA(Account.class));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifySupportsStorage() throws Exception {
    AccountCreationResult result =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "666666"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_HAS_STORAGE, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 2222, null),
                                    MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

    assertThat(result.getUuid()).isNotNull();
    assertThat(result.isStorageCapable()).isTrue();

    verify(accountsManager, times(1)).create(isA(Account.class));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyCodeOld() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "1234"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_OLD, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 2222, null),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);

    verifyNoMoreInteractions(accountsManager);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyBadCode() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "1111"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);

    verifyNoMoreInteractions(accountsManager);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyPin() throws Exception {
    AccountCreationResult result =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "333333"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_PIN, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, "31337"),
                                    MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

    assertThat(result.getUuid()).isNotNull();

    verify(pinLimiter).validate(eq(SENDER_PIN));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyRegistrationLock() throws Exception {
    AccountCreationResult result =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "666666"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_REG_LOCK, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null, null, Hex.toStringCondensed(registration_lock_key), null),
                                    MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

    assertThat(result.getUuid()).isNotNull();

    verify(pinLimiter).validate(eq(SENDER_REG_LOCK));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyRegistrationLockSetsRegistrationLockOnNewAccount() throws Exception {

    AccountCreationResult result =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "666666"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_REG_LOCK, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null, null, Hex.toStringCondensed(registration_lock_key), null),
                                    MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

    assertThat(result.getUuid()).isNotNull();

    verify(pinLimiter).validate(eq(SENDER_REG_LOCK));

    verify(accountsManager).create(argThat(new ArgumentMatcher<>() {
      @Override
      public boolean matches(final Account account) {
        final StoredRegistrationLock regLock = account.getRegistrationLock();
        return regLock.requiresClientRegistrationLock() && regLock.verify(Hex.toStringCondensed(registration_lock_key), null);
      }

      @Override
      public String toString() {
        return "Account that has registration lock set";
      }
    }));

  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyRegistrationLockOld() throws Exception {
    StoredRegistrationLock lock = senderRegLockAccount.getRegistrationLock();

    try {
      when(senderRegLockAccount.getRegistrationLock()).thenReturn(lock.forTime(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)));

      AccountCreationResult result =
          resources.getJerseyTest()
                   .target(String.format("/v1/accounts/code/%s", "666666"))
                   .request()
                   .header("Authorization", AuthHelper.getAuthHeader(SENDER_REG_LOCK, "bar"))
                   .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null, null, null, null),
                                      MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

      assertThat(result.getUuid()).isNotNull();

      verifyNoMoreInteractions(pinLimiter);
    } finally {
      when(senderRegLockAccount.getRegistrationLock()).thenReturn(lock);
    }
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyWrongPin() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "333333"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_PIN, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, "31338"),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(423);

    verify(pinLimiter).validate(eq(SENDER_PIN));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyWrongRegistrationLock() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "666666"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_REG_LOCK, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, Hex.toStringCondensed(new byte[32])),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(423);

    verify(pinLimiter).validate(eq(SENDER_REG_LOCK));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyNoPin() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "333333"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_PIN, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(423);

    RegistrationLockFailure failure = response.readEntity(RegistrationLockFailure.class);
    assertThat(failure.getBackupCredentials()).isNull();

    verifyNoMoreInteractions(pinLimiter);
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyNoRegistrationLock() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "666666"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_REG_LOCK, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(423);

    RegistrationLockFailure failure = response.readEntity(RegistrationLockFailure.class);
    assertThat(failure.getBackupCredentials()).isNotNull();
    assertThat(failure.getBackupCredentials().getUsername()).isEqualTo(SENDER_REG_LOCK_UUID.toString());
    assertThat(failure.getBackupCredentials().getPassword()).isNotEmpty();
    assertThat(failure.getBackupCredentials().getPassword().startsWith(SENDER_REG_LOCK_UUID.toString())).isTrue();
    assertThat(failure.getTimeRemaining()).isGreaterThan(0);

    verifyNoMoreInteractions(pinLimiter);
  }


  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyLimitPin() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target(String.format("/v1/accounts/code/%s", "444444"))
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(SENDER_OVER_PIN, "bar"))
                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, "31337"),
                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(413);

    verify(rateLimiter).clear(eq(SENDER_OVER_PIN));
  }

  @Test
  @Ignore("Deleted the REST endpoint")
  public void testVerifyOldPin() throws Exception {
    try {
      when(senderPinAccount.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), Optional.of("31337"), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)));

      AccountCreationResult result =
          resources.getJerseyTest()
                   .target(String.format("/v1/accounts/code/%s", "444444"))
                   .request()
                   .header("Authorization", AuthHelper.getAuthHeader(SENDER_OVER_PIN, "bar"))
                   .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 3333, null),
                                      MediaType.APPLICATION_JSON_TYPE), AccountCreationResult.class);

      assertThat(result.getUuid()).isNotNull();

    } finally {
      when(senderPinAccount.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), Optional.of("31337"), System.currentTimeMillis()));
    }
  }

  @Ignore("Diskuv does not support payments")
  @Test
  public void testVerifyTransferSupported() {
    when(senderTransfer.isTransferSupported()).thenReturn(true);

    final Response response =
            resources.getJerseyTest()
                    .target(String.format("/v1/accounts/code/%s", "1234"))
                    .queryParam("transfer", true)
                    .request()
                    .header("Authorization", AuthHelper.getAuthHeader(SENDER_TRANSFER, "bar"))
                    .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 2222, null),
                            MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(409);
  }

  @Ignore("Diskuv does not support payments")
  @Test
  public void testVerifyTransferNotSupported() {
    when(senderTransfer.isTransferSupported()).thenReturn(false);

    final Response response =
            resources.getJerseyTest()
                    .target(String.format("/v1/accounts/code/%s", "1234"))
                    .queryParam("transfer", true)
                    .request()
                    .header("Authorization", AuthHelper.getAuthHeader(SENDER_TRANSFER, "bar"))
                    .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 2222, null),
                            MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Ignore("Diskuv does not support payments")
  @Test
  public void testVerifyTransferSupportedNotRequested() {
    when(senderTransfer.isTransferSupported()).thenReturn(true);

    final Response response =
            resources.getJerseyTest()
                    .target(String.format("/v1/accounts/code/%s", "1234"))
                    .request()
                    .header("Authorization", AuthHelper.getAuthHeader(SENDER_TRANSFER, "bar"))
                    .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 2222, null),
                            MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testSetPin() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/pin/")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new DeprecatedPin("31337")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.VALID_ACCOUNT).setPin(eq("31337"));
    verify(AuthHelper.VALID_ACCOUNT).setRegistrationLock(eq(null), eq(null));
  }

  @Test
  public void testSetRegistrationLock() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/registration_lock/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new RegistrationLock("1234567890123456789012345678901234567890123456789012345678901234")));

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<String> pinCapture     = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> pinSaltCapture = ArgumentCaptor.forClass(String.class);

    verify(AuthHelper.VALID_ACCOUNT, times(1)).setPin(eq(null));
    verify(AuthHelper.VALID_ACCOUNT, times(1)).setRegistrationLock(pinCapture.capture(), pinSaltCapture.capture());

    assertThat(pinCapture.getValue()).isNotEmpty();
    assertThat(pinSaltCapture.getValue()).isNotEmpty();

    assertThat(pinCapture.getValue().length()).isEqualTo(40);
  }

  @Test
  public void testSetPayments() {
    PaymentAddress paymentAddress = new PaymentAddress("some address", "V15Pf5JsFcQF6AtlM3vo3OhGEgFwTh8G3iDDvShpr8QzoJmFQ+a2xb3PoXRmGF60DLq1RR2o8Fgw+f953mKvNA==");

    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/payments/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new PaymentAddressList(List.of(paymentAddress))));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.VALID_ACCOUNT, times(1)).setPayments(eq(List.of(paymentAddress)));
  }

  @Test
  public void testSetPaymentsUnauthorized() {
    PaymentAddress paymentAddress = new PaymentAddress("an address", "V15Pf5JsFcQF6AtlM3vo3OhGEgFwTh8G3iDDvShpr8QzoJmFQ+a2xb3PoXRmGF60DLq1RR2o8Fgw+f953mKvNA==");

    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/payments/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.INVALID_UUID.toString(), AuthHelper.INVALID_PASSWORD))
                 .put(Entity.json(new PaymentAddressList(List.of(paymentAddress))));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testSetPaymentsInvalidSignature() {
    PaymentAddress paymentAddress = new PaymentAddress("some address", "123456789012345678901234567890123");

    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/payments/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new PaymentAddressList(List.of(paymentAddress))));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testSetPaymentsEmptyAddress() {
    PaymentAddress paymentAddress = new PaymentAddress(null, "V15Pf5JsFcQF6AtlM3vo3OhGEgFwTh8G3iDDvShpr8QzoJmFQ+a2xb3PoXRmGF60DLq1RR2o8Fgw+f953mKvNA==");

    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/payments/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new PaymentAddressList(List.of(paymentAddress))));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testSetPaymentsEmptySignature() {
    PaymentAddress paymentAddress = new PaymentAddress("some address", null);

    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/payments/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new PaymentAddressList(List.of(paymentAddress))));

    assertThat(response.getStatus()).isEqualTo(422);
  }


  @Test
  public void testSetPinUnauthorized() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/pin/")
                 .request()
                 .put(Entity.json(new DeprecatedPin("31337")));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testSetShortPin() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/pin/")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new DeprecatedPin("313")));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testSetShortRegistrationLock() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/registration_lock/")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new RegistrationLock("313")));

    assertThat(response.getStatus()).isEqualTo(422);
  }


  @Test
  public void testSetPinDisabled() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/pin/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_NUMBER, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new DeprecatedPin("31337")));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testSetRegistrationLockDisabled() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/registration_lock/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_NUMBER, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new RegistrationLock("1234567890123456789012345678901234567890123456789012345678901234")));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testSetGcmId() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/gcm/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.DISABLED_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_DEVICE_ID_STRING, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new GcmRegistrationId("c00lz0rz")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setGcmId(eq("c00lz0rz"));
    verify(accountsManager, times(1)).update(eq(AuthHelper.DISABLED_ACCOUNT));
  }

  @Test
  public void testSetGcmIdByUuid() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/gcm/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.DISABLED_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_DEVICE_ID_STRING, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new GcmRegistrationId("z000")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setGcmId(eq("z000"));
    verify(accountsManager, times(1)).update(eq(AuthHelper.DISABLED_ACCOUNT));
  }

  @Test
  public void testSetApnId() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/apn/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.DISABLED_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_DEVICE_ID_STRING, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new ApnRegistrationId("first", "second")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setApnId(eq("first"));
    verify(AuthHelper.DISABLED_DEVICE, times(1)).setVoipApnId(eq("second"));
    verify(accountsManager, times(1)).update(eq(AuthHelper.DISABLED_ACCOUNT));
  }

  @Test
  public void testSetApnIdByUuid() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/apn/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.DISABLED_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_DEVICE_ID_STRING, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new ApnRegistrationId("third", "fourth")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setApnId(eq("third"));
    verify(AuthHelper.DISABLED_DEVICE, times(1)).setVoipApnId(eq("fourth"));
    verify(accountsManager, times(1)).update(eq(AuthHelper.DISABLED_ACCOUNT));
  }

  @Test
  public void testWhoAmI() {
    AccountCreationResult response =
        resources.getJerseyTest()
                 .target("/v1/accounts/whoami/")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .get(AccountCreationResult.class);

    assertThat(response.getUuid()).isEqualTo(AuthHelper.VALID_UUID);
  }

  @Test
  public void testSetUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/n00bkiller")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testSetTakenUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/takenusername")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(409);
  }

  @Test
  public void testSetInvalidUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/pаypal")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testSetInvalidPrefixUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/0n00bkiller")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testSetUsernameBadAuth() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/n00bkiller")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.INVALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.INVALID_DEVICE_ID_STRING, AuthHelper.INVALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testDeleteUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/")
                 .request()
                .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .delete();

    assertThat(response.getStatus()).isEqualTo(204);
    verify(usernamesManager, times(1)).delete(eq(AuthHelper.VALID_UUID));
  }

  @Test
  public void testDeleteUsernameBadAuth() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.INVALID_BEARER_TOKEN))
                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.INVALID_DEVICE_ID_STRING, AuthHelper.INVALID_PASSWORD))
                 .delete();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  private void validatePushChallengeAuthentication(String challenge, String pushToken, UUID accountUuid) {
    // NONCE = RANDOM_BYTES(16)
    byte[] nonce;
    try {
      nonce = org.apache.commons.codec.binary.Hex.decodeHex(challenge.substring(0, 32));
    } catch (DecoderException e) {
      throw new AssertionError("Received a non-hex encoded push challenge", e);
    }

    // SECRET = UTF8_BYTES(PUSH_TOKEN || ACCOUNT_UUID)
    byte[] secret = (pushToken + accountUuid).getBytes(StandardCharsets.UTF_8);

    // PUSH_CHALLENGE = HEX(NONCE || HMAC_SHA256(secret=SECRET, data=NONCE))
    byte[] digestActual;
    try {
      digestActual = org.apache.commons.codec.binary.Hex.decodeHex(challenge.substring(32));
    } catch (DecoderException e) {
      throw new AssertionError("Received a non-hex encoded push challenge", e);
    }
    byte[] digestExpected;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      digestExpected = mac.doFinal(nonce);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError("Can't create a push challenge signature", e);
    }

    assertThat(ByteString.copyFrom(digestActual)).isEqualTo(ByteString.copyFrom(digestExpected));
  }
}
