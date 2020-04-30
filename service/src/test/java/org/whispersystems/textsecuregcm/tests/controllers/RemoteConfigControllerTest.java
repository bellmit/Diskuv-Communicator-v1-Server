package org.whispersystems.textsecuregcm.tests.controllers;

import com.google.common.collect.ImmutableSet;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.controllers.RemoteConfigController;
import org.whispersystems.textsecuregcm.entities.UserRemoteConfigList;
import org.whispersystems.textsecuregcm.mappers.DeviceLimitExceededExceptionMapper;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.RemoteConfig;
import org.whispersystems.textsecuregcm.storage.RemoteConfigsManager;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RemoteConfigControllerTest {

  private final RemoteConfigsManager remoteConfigsManager = mock(RemoteConfigsManager.class);
  private final List<String>         remoteConfigsAuth    = List.of("foo", "bar");

  @Rule
  public final ResourceTestRule resources = ResourceTestRule.builder()
                                                            .addProvider(AuthHelper.getAuthFilter())
                                                            .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                                                            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                                                            .addProvider(new DeviceLimitExceededExceptionMapper())
                                                            .addResource(new RemoteConfigController(remoteConfigsManager, remoteConfigsAuth))
                                                            .build();


  @Before
  public void setup() {
    when(remoteConfigsManager.getAll()).thenReturn(new LinkedList<>() {{
      add(new RemoteConfig("android.stickers", 25, Set.of(AuthHelper.DISABLED_UUID, AuthHelper.INVALID_UUID), null, null));
      add(new RemoteConfig("ios.stickers", 50, Set.of(), null, null));
      add(new RemoteConfig("always.true", 100, Set.of(), null, null));
      add(new RemoteConfig("only.special", 0, Set.of(AuthHelper.VALID_UUID), null, null));
      add(new RemoteConfig("value.always.true", 100, Set.of(), "foo", "bar"));
      add(new RemoteConfig("value.only.special", 0, Set.of(AuthHelper.VALID_UUID), "abc", "xyz"));
      add(new RemoteConfig("value.always.false", 0, Set.of(), "red", "green"));
    }});
  }

  @Test
  public void testRetrieveConfig() {
    UserRemoteConfigList configuration = resources.getJerseyTest()
                                                  .target("/v1/config/")
                                                  .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                                  .get(UserRemoteConfigList.class);

    verify(remoteConfigsManager, times(1)).getAll();

    assertThat(configuration.getConfig().size()).isEqualTo(7);
    assertThat(configuration.getConfig().get(0).getName()).isEqualTo("android.stickers");
    assertThat(configuration.getConfig().get(1).getName()).isEqualTo("ios.stickers");
    assertThat(configuration.getConfig().get(2).getName()).isEqualTo("always.true");
    assertThat(configuration.getConfig().get(2).isEnabled()).isEqualTo(true);
    assertThat(configuration.getConfig().get(2).getValue()).isNull();
    assertThat(configuration.getConfig().get(3).getName()).isEqualTo("only.special");
    assertThat(configuration.getConfig().get(3).isEnabled()).isEqualTo(true);
    assertThat(configuration.getConfig().get(2).getValue()).isNull();
    assertThat(configuration.getConfig().get(4).getName()).isEqualTo("value.always.true");
    assertThat(configuration.getConfig().get(4).isEnabled()).isEqualTo(true);
    assertThat(configuration.getConfig().get(4).getValue()).isEqualTo("bar");
    assertThat(configuration.getConfig().get(5).getName()).isEqualTo("value.only.special");
    assertThat(configuration.getConfig().get(5).isEnabled()).isEqualTo(true);
    assertThat(configuration.getConfig().get(5).getValue()).isEqualTo("xyz");
    assertThat(configuration.getConfig().get(6).getName()).isEqualTo("value.always.false");
    assertThat(configuration.getConfig().get(6).isEnabled()).isEqualTo(false);
    assertThat(configuration.getConfig().get(6).getValue()).isEqualTo("red");
  }

  @Test
  public void testRetrieveConfigNotSpecial() {
    UserRemoteConfigList configuration = resources.getJerseyTest()
                                                  .target("/v1/config/")
                                                  .request()
                                                  .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                                  .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_UUID_TWO, AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                                  .get(UserRemoteConfigList.class);

    verify(remoteConfigsManager, times(1)).getAll();

    assertThat(configuration.getConfig().size()).isEqualTo(7);
    assertThat(configuration.getConfig().get(0).getName()).isEqualTo("android.stickers");
    assertThat(configuration.getConfig().get(1).getName()).isEqualTo("ios.stickers");
    assertThat(configuration.getConfig().get(2).getName()).isEqualTo("always.true");
    assertThat(configuration.getConfig().get(2).isEnabled()).isEqualTo(true);
    assertThat(configuration.getConfig().get(2).getValue()).isNull();
    assertThat(configuration.getConfig().get(3).getName()).isEqualTo("only.special");
    assertThat(configuration.getConfig().get(3).isEnabled()).isEqualTo(false);
    assertThat(configuration.getConfig().get(2).getValue()).isNull();
    assertThat(configuration.getConfig().get(4).getName()).isEqualTo("value.always.true");
    assertThat(configuration.getConfig().get(4).isEnabled()).isEqualTo(true);
    assertThat(configuration.getConfig().get(4).getValue()).isEqualTo("bar");
    assertThat(configuration.getConfig().get(5).getName()).isEqualTo("value.only.special");
    assertThat(configuration.getConfig().get(5).isEnabled()).isEqualTo(false);
    assertThat(configuration.getConfig().get(5).getValue()).isEqualTo("abc");
    assertThat(configuration.getConfig().get(6).getName()).isEqualTo("value.always.false");
    assertThat(configuration.getConfig().get(6).isEnabled()).isEqualTo(false);
    assertThat(configuration.getConfig().get(6).getValue()).isEqualTo("red");
  }


  @Test
  public void testRetrieveConfigUnauthorized() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config/")
                                 .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.INVALID_PASSWORD))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);

    verifyNoMoreInteractions(remoteConfigsManager);
  }


  @Test
  public void testSetConfig() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config")
                                 .request()
                                 .header("Config-Token", "foo")
                                 .put(Entity.entity(new RemoteConfig("android.stickers", 88, Set.of(), "FALSE", "TRUE"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<RemoteConfig> captor = ArgumentCaptor.forClass(RemoteConfig.class);

    verify(remoteConfigsManager, times(1)).set(captor.capture());

    assertThat(captor.getValue().getName()).isEqualTo("android.stickers");
    assertThat(captor.getValue().getPercentage()).isEqualTo(88);
    assertThat(captor.getValue().getUuids()).isEmpty();
  }

  @Test
  public void testSetConfigValued() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config")
                                 .request()
                                 .header("Config-Token", "foo")
                                 .put(Entity.entity(new RemoteConfig("value.sometimes", 50, Set.of(), "a", "b"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<RemoteConfig> captor = ArgumentCaptor.forClass(RemoteConfig.class);

    verify(remoteConfigsManager, times(1)).set(captor.capture());

    assertThat(captor.getValue().getName()).isEqualTo("value.sometimes");
    assertThat(captor.getValue().getPercentage()).isEqualTo(50);
    assertThat(captor.getValue().getUuids()).isEmpty();
  }

  @Test
  public void testSetConfigUnauthorized() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config")
                                 .request()
                                 .header("Config-Token", "baz")
                                 .put(Entity.entity(new RemoteConfig("android.stickers", 88, Set.of(), "FALSE", "TRUE"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(401);

    verifyNoMoreInteractions(remoteConfigsManager);
  }

  @Test
  public void testSetConfigMissingUnauthorized() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config")
                                 .request()
                                 .put(Entity.entity(new RemoteConfig("android.stickers", 88, Set.of(), "FALSE", "TRUE"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(401);

    verifyNoMoreInteractions(remoteConfigsManager);
  }

  @Test
  public void testSetConfigBadName() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config")
                                 .request()
                                 .header("Config-Token", "foo")
                                 .put(Entity.entity(new RemoteConfig("android-stickers", 88, Set.of(), "FALSE", "TRUE"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(422);

    verifyNoMoreInteractions(remoteConfigsManager);
  }

  @Test
  public void testSetConfigEmptyName() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config")
                                 .request()
                                 .header("Config-Token", "foo")
                                 .put(Entity.entity(new RemoteConfig("", 88, Set.of(), "FALSE", "TRUE"), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(422);

    verifyNoMoreInteractions(remoteConfigsManager);
  }


  @Test
  public void testDelete() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config/android.stickers")
                                 .request()
                                 .header("Config-Token", "foo")
                                 .delete();

    assertThat(response.getStatus()).isEqualTo(204);

    verify(remoteConfigsManager, times(1)).delete("android.stickers");
    verifyNoMoreInteractions(remoteConfigsManager);
  }

  @Test
  public void testDeleteUnauthorized() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/config/android.stickers")
                                 .request()
                                 .header("Config-Token", "baz")
                                 .delete();

    assertThat(response.getStatus()).isEqualTo(401);

    verifyNoMoreInteractions(remoteConfigsManager);
  }

  @Test
  public void testMath() throws NoSuchAlgorithmException {
    List<RemoteConfig>   remoteConfigList = remoteConfigsManager.getAll();
    Map<String, Integer> enabledMap       = new HashMap<>();
    MessageDigest        digest           = MessageDigest.getInstance("SHA1");
    int                  iterations       = 100000;
    SecureRandom         secureRandom     = new SecureRandom(new byte[]{42});  // the seed value doesn't matter so much as it's constant to make the test not flaky

    for (int i=0;i<iterations;i++) {
      for (RemoteConfig config : remoteConfigList) {
        int count  = enabledMap.getOrDefault(config.getName(), 0);

        if (RemoteConfigController.isInBucket(digest, getRandomUUID(secureRandom), config.getName().getBytes(), config.getPercentage(), new HashSet<>())) {
          count++;
        }

        enabledMap.put(config.getName(), count);
      }
    }

    for (RemoteConfig config : remoteConfigList) {
      double targetNumber = iterations   * (config.getPercentage() / 100.0);
      double variance     = targetNumber * 0.01;

      assertThat(enabledMap.get(config.getName())).isBetween((int)(targetNumber - variance), (int)(targetNumber + variance));
    }

  }

  private static UUID getRandomUUID(SecureRandom secureRandom) {
    long mostSignificantBits  = secureRandom.nextLong();
    long leastSignificantBits = secureRandom.nextLong();
    mostSignificantBits  &= 0xffffffffffff0fffL;
    mostSignificantBits  |= 0x0000000000004000L;
    leastSignificantBits &= 0x3fffffffffffffffL;
    leastSignificantBits |= 0x8000000000000000L;
    return new UUID(mostSignificantBits, leastSignificantBits);
  }
}
