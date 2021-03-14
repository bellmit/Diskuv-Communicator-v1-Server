package org.whispersystems.textsecuregcm.tests.controllers;

import com.amazonaws.services.s3.AmazonS3;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.mockito.ArgumentMatcher;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.controllers.ProfileController;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.PolicySigner;
import org.whispersystems.textsecuregcm.s3.PostPolicyGenerator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.ProfilesManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.storage.VersionedProfile;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.clearInvocations;

public class DiskuvProfilerControllerTest {
    private static AccountsManager accountsManager     = mock(AccountsManager.class );
    private static ProfilesManager profilesManager     = mock(ProfilesManager.class);
    private static UsernamesManager usernamesManager    = mock(UsernamesManager.class);
    private static RateLimiters rateLimiters        = mock(RateLimiters.class    );
    private static RateLimiter rateLimiter         = mock(RateLimiter.class     );
    private static RateLimiter      usernameRateLimiter = mock(RateLimiter.class     );

    private static AmazonS3 s3client            = mock(AmazonS3.class);
    private static PostPolicyGenerator postPolicyGenerator = new PostPolicyGenerator("us-west-1", "profile-bucket", "accessKey");
    private static PolicySigner policySigner        = new PolicySigner("accessSecret", "us-west-1");
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

        when(accountsManager.get(AuthHelper.VALID_NUMBER_TWO)).thenReturn(Optional.of(profileAccount));
        when(accountsManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of(profileAccount));
        when(usernamesManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of("n00bkiller"));
        when(usernamesManager.get("n00bkiller")).thenReturn(Optional.of(AuthHelper.VALID_UUID_TWO));
        when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(AuthHelper.VALID_NUMBER_TWO)))).thenReturn(Optional.of(profileAccount));
        when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(AuthHelper.VALID_UUID_TWO)))).thenReturn(Optional.of(profileAccount));

        when(accountsManager.get(AuthHelper.VALID_NUMBER)).thenReturn(Optional.of(capabilitiesAccount));
        when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasNumber() && identifier.getNumber().equals(AuthHelper.VALID_NUMBER)))).thenReturn(Optional.of(capabilitiesAccount));

        when(profilesManager.get(eq(AuthHelper.VALID_UUID), eq("someversion"))).thenReturn(Optional.empty());
        when(profilesManager.get(eq(AuthHelper.VALID_UUID_TWO), eq("validversion"))).thenReturn(Optional.of(new VersionedProfile("validversion", "validname", "profiles/validavatar", "validcommitmnet".getBytes())));

        clearInvocations(rateLimiter);
        clearInvocations(accountsManager);
        clearInvocations(usernamesManager);
        clearInvocations(usernameRateLimiter);
        clearInvocations(profilesManager);
    }


}
