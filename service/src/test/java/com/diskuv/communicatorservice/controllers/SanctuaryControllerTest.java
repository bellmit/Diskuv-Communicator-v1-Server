package com.diskuv.communicatorservice.controllers;

import com.diskuv.communicatorservice.storage.SanctuaryAttributes;
import com.diskuv.communicatorservice.storage.SanctuaryItem;
import com.diskuv.communicatorservice.storage.SanctuariesDao;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.signal.storageservice.auth.User;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SanctuaryControllerTest {

  private RateLimiters rateLimiters;

  @Before
  public void setUp() {
    RateLimiter rateLimiter = mock(RateLimiter.class);
    this.rateLimiters = mock(RateLimiters.class);
    doReturn(rateLimiter).when(rateLimiters).getSanctuaryLookupLimiter();
  }

  @Test
  public void given_noEmailAddressesAllowedToDeploySanctuary_when_createSanctuary_then_unauthorized() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();

    // when
    DynamoDbAsyncClient asyncClient = mock(DynamoDbAsyncClient.class);
    SanctuaryController controller =
        new SanctuaryController(asyncClient, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(UUID.randomUUID());
    String sanctuaryGroupIdHex = Hex.encodeHexString(new byte[] {1, 2, 3});
    SanctuaryAttributes sanctuaryAttributes = new SanctuaryAttributes();
    CompletableFuture<Response> future =
        controller.createSanctuary(user, sanctuaryGroupIdHex, sanctuaryAttributes);

    // then
    Response response = future.join();
    assertThat(
        response.getStatus(), CoreMatchers.equalTo(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void
      given_oneEmailAddressAllowedToDeploySanctuary_when_createSanctuary_withAllowedEmail_then_authorized() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    String allowedEmailAddress = "test@test.com";
    diskuvGroupsConfiguration.setEmailAddressesAllowedToDeploySanctuary(
        ImmutableList.of(allowedEmailAddress));

    // when
    DynamoDbAsyncClient asyncClient = mock(DynamoDbAsyncClient.class);
    doReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()))
        .when(asyncClient)
        .putItem(any(PutItemRequest.class));
    SanctuaryController controller =
        new SanctuaryController(asyncClient, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress(allowedEmailAddress));
    String sanctuaryGroupIdHex = Hex.encodeHexString(new byte[] {1, 2, 3});
    UUID supportContactId = UUID.randomUUID();
    SanctuaryAttributes sanctuaryAttributes = new SanctuaryAttributes(supportContactId);
    CompletableFuture<Response> future =
        controller.createSanctuary(user, sanctuaryGroupIdHex, sanctuaryAttributes);

    // then
    Response response = future.join();
    assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void given_noSanctuaries_when_updateSanctuary_then_notFound() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    String allowedEmailAddress = "test@test.com";
    diskuvGroupsConfiguration.setEmailAddressesAllowedToDeploySanctuary(
        ImmutableList.of(allowedEmailAddress));

    // when
    DynamoDbAsyncClient asyncClient = mock(DynamoDbAsyncClient.class);
    doReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()))
        .when(asyncClient)
        .getItem(any(GetItemRequest.class));
    doReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()))
        .when(asyncClient)
        .putItem(any(PutItemRequest.class));
    SanctuaryController controller =
        new SanctuaryController(asyncClient, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress(allowedEmailAddress));
    String sanctuaryGroupIdHex = Hex.encodeHexString(new byte[] {1, 2, 3});
    UUID supportContactId = UUID.randomUUID();
    SanctuaryAttributes sanctuaryAttributes = new SanctuaryAttributes(supportContactId);
    CompletableFuture<Response> future =
        controller.updateSanctuary(user, sanctuaryGroupIdHex, sanctuaryAttributes);

    // then
    Response response = future.join();
    assertThat(
        response.getStatus(), CoreMatchers.equalTo(Response.Status.NOT_FOUND.getStatusCode()));
  }

  @Test
  public void given_oneSanctuary_when_updateSanctuary_then_ok() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    String allowedEmailAddress = "test@test.com";
    diskuvGroupsConfiguration.setEmailAddressesAllowedToDeploySanctuary(
        ImmutableList.of(allowedEmailAddress));

    // when
    SanctuariesDao sanctuariesDao = mock(SanctuariesDao.class);
    doReturn(CompletableFuture.completedFuture(Optional.of(new SanctuaryItem())))
        .when(sanctuariesDao)
        .getSanctuary(any(ByteString.class));
    doReturn(CompletableFuture.completedFuture(null))
        .when(sanctuariesDao)
        .updateSanctuary(any(SanctuaryItem.class));
    SanctuaryController controller =
        new SanctuaryController(sanctuariesDao, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress(allowedEmailAddress));
    String sanctuaryGroupIdHex = Hex.encodeHexString(new byte[] {1, 2, 3});
    UUID supportContactId = UUID.randomUUID();
    SanctuaryAttributes sanctuaryAttributes = new SanctuaryAttributes(supportContactId);
    CompletableFuture<Response> future =
        controller.updateSanctuary(user, sanctuaryGroupIdHex, sanctuaryAttributes);

    // then
    Response response = future.join();
    assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void given_oneSanctuary_when_getSanctuary_then_oneSanctuary() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    SanctuaryItem oneSanctuary = new SanctuaryItem();
    UUID contactUuid = UUID.randomUUID();
    oneSanctuary.setSupportContactId(contactUuid.toString());
    oneSanctuary.setSanctuaryEnabled(true);

    // when
    SanctuariesDao sanctuariesDao = mock(SanctuariesDao.class);
    doReturn(CompletableFuture.completedFuture(Optional.of(oneSanctuary)))
        .when(sanctuariesDao)
        .getSanctuary(any(ByteString.class));
    SanctuaryController controller =
        new SanctuaryController(sanctuariesDao, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress("blah@test.com"));
    String sanctuaryGroupIdHex = Hex.encodeHexString(new byte[] {1, 2, 3});
    AsyncResponse asyncResponse = mock(AsyncResponse.class);
    controller.getSanctuary(user, sanctuaryGroupIdHex, asyncResponse);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(asyncResponse).resume(captor.capture());
    Response response = captor.getValue();

    // then
    assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
    assertThat(response.getEntity(), CoreMatchers.instanceOf(SanctuaryAttributes.class));
    SanctuaryAttributes attr = (SanctuaryAttributes) response.getEntity();
    assertTrue(attr.isSanctuaryEnabled());
    assertThat(attr.getSupportContactId(), CoreMatchers.equalTo(contactUuid));
  }
}
