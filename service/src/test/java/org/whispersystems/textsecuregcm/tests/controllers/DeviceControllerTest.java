/**
 * Copyright (C) 2014 Open WhisperSystems
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
package org.whispersystems.textsecuregcm.tests.controllers;

import com.google.common.collect.ImmutableSet;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import org.whispersystems.textsecuregcm.auth.StoredVerificationCode;
import org.whispersystems.textsecuregcm.controllers.DeviceController;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.DeviceResponse;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.mappers.DeviceLimitExceededExceptionMapper;
import org.whispersystems.textsecuregcm.sqs.DirectoryQueue;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.PendingDevicesManager;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;
import org.whispersystems.textsecuregcm.util.VerificationCode;

import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.whispersystems.textsecuregcm.tests.util.AuthHelper.*;

public class DeviceControllerTest {
  @Path("/v1/devices")
  static class DumbVerificationDeviceController extends DeviceController {
    public DumbVerificationDeviceController(PendingDevicesManager pendingDevices,
                                            AccountsManager accounts,
                                            JwtAuthentication jwtAuthentication,
                                            MessagesManager messages,
                                            DirectoryQueue cdsSender,
                                            RateLimiters rateLimiters,
                                            Map<String, Integer> deviceConfiguration)
    {
      super(pendingDevices, accounts, jwtAuthentication, messages, rateLimiters, deviceConfiguration);
    }

    @Override
    protected VerificationCode generateVerificationCode() {
      return new VerificationCode(5678901);
    }
  }

  private PendingDevicesManager pendingDevicesManager = mock(PendingDevicesManager.class);
  private AccountsManager       accountsManager       = mock(AccountsManager.class       );
  private JwtAuthentication     jwtAuthentication     = mock(JwtAuthentication.class);
  private MessagesManager       messagesManager       = mock(MessagesManager.class);
  private DirectoryQueue        directoryQueue        = mock(DirectoryQueue.class);
  private RateLimiters          rateLimiters          = mock(RateLimiters.class          );
  private RateLimiter           rateLimiter           = mock(RateLimiter.class           );
  private Account               account               = mock(Account.class               );
  private Account               maxedAccount          = mock(Account.class);
  private Device                masterDevice          = mock(Device.class);

  private Map<String, Integer>  deviceConfiguration   = new HashMap<String, Integer>() {{

  }};

  @Rule
  public final ResourceTestRule resources = ResourceTestRule.builder()
                                                            .addProvider(AuthHelper.getAuthFilter())
                                                            .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                                                            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                                                            .addProvider(new DeviceLimitExceededExceptionMapper())
                                                            .addResource(new DumbVerificationDeviceController(pendingDevicesManager,
                                                                                                              accountsManager,
                                                                                                              jwtAuthentication,
                                                                                                              messagesManager,
                                                                                                              directoryQueue,
                                                                                                              rateLimiters,
                                                                                                              deviceConfiguration))
                                                            .build();


  @Before
  public void setup() throws Exception {
    when(rateLimiters.getSmsDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVoiceDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVerifyLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getAllocateDeviceLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVerifyDeviceLimiter()).thenReturn(rateLimiter);

    when(masterDevice.getId()).thenReturn(1L);

    when(account.getNextDeviceId()).thenReturn(42L);
    when(account.getNumber()).thenReturn(AuthHelper.VALID_NUMBER);
//    when(maxedAccount.getActiveDeviceCount()).thenReturn(6);
    when(account.getAuthenticatedDevice()).thenReturn(Optional.of(masterDevice));
    when(account.isEnabled()).thenReturn(false);

    when(pendingDevicesManager.getCodeForPendingDevice(AuthHelper.VALID_UUID)).thenReturn(Optional.of(new StoredVerificationCode("5678901", System.currentTimeMillis(), null)));
    when(pendingDevicesManager.getCodeForPendingDevice(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of(new StoredVerificationCode("1112223", System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(31), null)));
    when(accountsManager.get(AuthHelper.VALID_UUID)).thenReturn(Optional.of(account));
    when(accountsManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of(maxedAccount));

    when(jwtAuthentication.verifyBearerTokenAndGetEmailAddress(VALID_BEARER_TOKEN)).thenReturn(VALID_EMAIL);
    when(jwtAuthentication.verifyBearerTokenAndGetEmailAddress(VALID_BEARER_TOKEN_TWO)).thenReturn(VALID_EMAIL_TWO);
  }

  @Test
  public void validDeviceRegisterTest() throws Exception {
    VerificationCode deviceCode = resources.getJerseyTest()
                                           .target("/v1/devices/provisioning/code")
                                           .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                           .get(VerificationCode.class);

    assertThat(deviceCode).isEqualTo(new VerificationCode(5678901));

    DeviceResponse response = resources.getJerseyTest()
                                       .target("/v1/devices/5678901")
                                       .request()
                                       .header("Authorization", AuthHelper.getAccountAuthHeader(VALID_BEARER_TOKEN))
                                       .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                       .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 1234, null),
                                                          MediaType.APPLICATION_JSON_TYPE),
                                            DeviceResponse.class);

    assertThat(response.getDeviceId()).isEqualTo(42L);

    verify(pendingDevicesManager).remove(AuthHelper.VALID_UUID);
    verify(messagesManager).clear(eq(AuthHelper.VALID_UUID.toString()), eq(42L));
  }

  @Test
  public void disabledDeviceRegisterTest() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/devices/provisioning/code")
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_NUMBER, AuthHelper.DISABLED_PASSWORD))
                                 .get();

      assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void invalidDeviceRegisterTest() throws Exception {
    VerificationCode deviceCode = resources.getJerseyTest()
                                           .target("/v1/devices/provisioning/code")
                                           .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(VALID_BEARER_TOKEN))
            .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                           .get(VerificationCode.class);

    assertThat(deviceCode).isEqualTo(new VerificationCode(5678901));

    Response response = resources.getJerseyTest()
                                 .target("/v1/devices/5678902")
                                 .request()
                                  .header("Authorization", AuthHelper.getAccountAuthHeader(VALID_BEARER_TOKEN))
                                  .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 1234, null),
                                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);

    verifyNoMoreInteractions(messagesManager);
  }

  @Test
  public void oldDeviceRegisterTest() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/devices/1112223")
                                 .request()
                                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                 .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 1234, null),
                                                    MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(403);

    verifyNoMoreInteractions(messagesManager);
  }

  @Test
  public void maxDevicesTest() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/devices/provisioning/code")
                                 .request()
                                  .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN_TWO))
                                  .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING_TWO, AuthHelper.VALID_PASSWORD_TWO))
                                 .get();

    assertEquals(411, response.getStatus());
    verifyNoMoreInteractions(messagesManager);
  }

  @Test
  public void longNameTest() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/devices/5678901")
                                 .request()
                                .header("Authorization", AuthHelper.getAccountAuthHeader(VALID_BEARER_TOKEN))
                                .header(DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.entity(new AccountAttributes("keykeykeykey", false, 1234, "this is a really long name that is longer than 80 characters it's so long that it's even longer than 204 characters. that's a lot of characters. we're talking lots and lots and lots of characters. 12345678", null, null),
                                                    MediaType.APPLICATION_JSON_TYPE));

    assertEquals(response.getStatus(), 422);
    verifyNoMoreInteractions(messagesManager);
  }

}
