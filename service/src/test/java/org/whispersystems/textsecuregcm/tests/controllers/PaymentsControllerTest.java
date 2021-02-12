package org.whispersystems.textsecuregcm.tests.controllers;

import com.google.common.collect.ImmutableSet;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentialGenerator;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentials;
import org.whispersystems.textsecuregcm.controllers.PaymentsController;
import org.whispersystems.textsecuregcm.currency.CurrencyConversionManager;
import org.whispersystems.textsecuregcm.entities.CurrencyConversionEntity;
import org.whispersystems.textsecuregcm.entities.CurrencyConversionEntityList;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore("Diskuv does not do payments")
public class PaymentsControllerTest {

  private static final ExternalServiceCredentialGenerator paymentsCredentialGenerator = mock(ExternalServiceCredentialGenerator.class);
  private static final CurrencyConversionManager currencyManager                      = mock(CurrencyConversionManager.class);

  private final ExternalServiceCredentials validCredentials = new ExternalServiceCredentials("username", "password");

  @ClassRule
  public static final ResourceTestRule resources = ResourceTestRule.builder()
                                                                   .addProvider(AuthHelper.getAuthFilter())
                                                                   .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                                                                   .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                                                                   .addResource(new PaymentsController(currencyManager, paymentsCredentialGenerator))
                                                                   .build();


  @Before
  public void setup() {
    when(paymentsCredentialGenerator.generateFor(eq(AuthHelper.VALID_UUID.toString()))).thenReturn(validCredentials);
    when(currencyManager.getCurrencyConversions()).thenReturn(Optional.of(new CurrencyConversionEntityList(List.of(new CurrencyConversionEntity("FOO", Map.of("USD", 2.35, "EUR", 1.89)), new CurrencyConversionEntity("BAR", Map.of("USD", 1.50, "EUR", 0.98))), System.currentTimeMillis())));
  }

  @Test
  public void testGetAuthToken() {
    ExternalServiceCredentials token =
        resources.getJerseyTest()
            .target("/v1/payments/auth")
            .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
            .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
            .get(ExternalServiceCredentials.class);

    assertThat(token.getUsername()).isEqualTo(validCredentials.getUsername());
    assertThat(token.getPassword()).isEqualTo(validCredentials.getPassword());
  }

  @Test
  public void testInvalidAuthGetAuthToken() {
    Response response =
        resources.getJerseyTest()
            .target("/v1/payments/auth")
            .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.INVALID_BEARER_TOKEN))
            .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.INVALID_DEVICE_ID_STRING, AuthHelper.INVALID_PASSWORD))
            .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testDisabledGetAuthToken() {
    Response response =
        resources.getJerseyTest()
            .target("/v1/payments/auth")
            .request()
            .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.DISABLED_BEARER_TOKEN))
            .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.DISABLED_DEVICE_ID_STRING, AuthHelper.DISABLED_PASSWORD))
            .get();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void testGetCurrencyConversions() {
    CurrencyConversionEntityList conversions =
        resources.getJerseyTest()
                 .target("/v1/payments/conversions")
                 .request()
                 .header("Authorization", AuthHelper.getAccountAuthHeader(AuthHelper.VALID_BEARER_TOKEN))
                 .header(com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER, AuthHelper.getAuthHeader(AuthHelper.VALID_DEVICE_ID_STRING, AuthHelper.VALID_PASSWORD))
                 .get(CurrencyConversionEntityList.class);


    assertThat(conversions.getCurrencies().size()).isEqualTo(2);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(2.35);
  }

}
