package com.diskuv.communicatorservice.controllers;

import com.diskuv.communicatorservice.storage.HouseAttributes;
import com.diskuv.communicatorservice.storage.HouseItem;
import com.diskuv.communicatorservice.storage.HousesDao;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Base64;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.signal.storageservice.auth.User;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class HouseControllerTest {

  private RateLimiters rateLimiters;

  @Before
  public void setUp() {
    RateLimiter rateLimiter = mock(RateLimiter.class);
    this.rateLimiters = mock(RateLimiters.class);
    doReturn(rateLimiter).when(rateLimiters).getHouseLookupLimiter();
  }

  @Test
  public void given_noEmailAddressesAllowedToDeployHouse_when_createHouse_then_unauthorized() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();

    // when
    DynamoDbAsyncClient asyncClient = mock(DynamoDbAsyncClient.class);
    HouseController controller =
        new HouseController(asyncClient, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(UUID.randomUUID());
    String houseGroupIdBase64 = Base64.encodeBase64String(new byte[] {1, 2, 3});
    HouseAttributes houseAttributes = new HouseAttributes();
    CompletableFuture<Response> future =
        controller.createHouse(user, houseGroupIdBase64, houseAttributes);

    // then
    Response response = future.join();
    assertThat(
        response.getStatus(), CoreMatchers.equalTo(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void
      given_oneEmailAddressAllowedToDeployHouse_when_createHouse_withAllowedEmail_then_authorized() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    String allowedEmailAddress = "test@test.com";
    diskuvGroupsConfiguration.setEmailAddressesAllowedToDeployHouse(
        ImmutableList.of(allowedEmailAddress));

    // when
    DynamoDbAsyncClient asyncClient = mock(DynamoDbAsyncClient.class);
    doReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()))
        .when(asyncClient)
        .putItem(any(PutItemRequest.class));
    HouseController controller =
        new HouseController(asyncClient, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress(allowedEmailAddress));
    String houseGroupIdBase64 = Base64.encodeBase64String(new byte[] {1, 2, 3});
    UUID supportContactId = UUID.randomUUID();
    HouseAttributes houseAttributes = new HouseAttributes(supportContactId);
    CompletableFuture<Response> future =
        controller.createHouse(user, houseGroupIdBase64, houseAttributes);

    // then
    Response response = future.join();
    assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void given_noHouses_when_updateHouse_then_notFound() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    String allowedEmailAddress = "test@test.com";
    diskuvGroupsConfiguration.setEmailAddressesAllowedToDeployHouse(
        ImmutableList.of(allowedEmailAddress));

    // when
    DynamoDbAsyncClient asyncClient = mock(DynamoDbAsyncClient.class);
    doReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()))
        .when(asyncClient)
        .getItem(any(GetItemRequest.class));
    doReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()))
        .when(asyncClient)
        .putItem(any(PutItemRequest.class));
    HouseController controller =
        new HouseController(asyncClient, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress(allowedEmailAddress));
    String houseGroupIdBase64 = Base64.encodeBase64String(new byte[] {1, 2, 3});
    UUID supportContactId = UUID.randomUUID();
    HouseAttributes houseAttributes = new HouseAttributes(supportContactId);
    CompletableFuture<Response> future =
        controller.updateHouse(user, houseGroupIdBase64, houseAttributes);

    // then
    Response response = future.join();
    assertThat(
        response.getStatus(), CoreMatchers.equalTo(Response.Status.NOT_FOUND.getStatusCode()));
  }

  @Test
  public void given_oneHouse_when_updateHouse_then_ok() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    String allowedEmailAddress = "test@test.com";
    diskuvGroupsConfiguration.setEmailAddressesAllowedToDeployHouse(
        ImmutableList.of(allowedEmailAddress));

    // when
    HousesDao housesDao = mock(HousesDao.class);
    doReturn(CompletableFuture.completedFuture(Optional.of(new HouseItem())))
        .when(housesDao)
        .getHouse(any(ByteString.class));
    doReturn(CompletableFuture.completedFuture(null))
        .when(housesDao)
        .updateHouse(any(HouseItem.class));
    HouseController controller =
        new HouseController(housesDao, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress(allowedEmailAddress));
    String houseGroupIdBase64 = Base64.encodeBase64String(new byte[] {1, 2, 3});
    UUID supportContactId = UUID.randomUUID();
    HouseAttributes houseAttributes = new HouseAttributes(supportContactId);
    CompletableFuture<Response> future =
        controller.updateHouse(user, houseGroupIdBase64, houseAttributes);

    // then
    Response response = future.join();
    assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void given_oneHouse_when_getHouse_then_ok() {
    // given
    DiskuvGroupsConfiguration diskuvGroupsConfiguration = new DiskuvGroupsConfiguration();
    HouseItem oneHouse = new HouseItem();

    // when
    HousesDao housesDao = mock(HousesDao.class);
    doReturn(CompletableFuture.completedFuture(Optional.of(oneHouse)))
        .when(housesDao)
        .getHouse(any(ByteString.class));
    HouseController controller =
        new HouseController(housesDao, diskuvGroupsConfiguration, rateLimiters);
    User user = new User(DiskuvUuidUtil.uuidForOutdoorEmailAddress("blah@test.com"));
    String houseGroupIdBase64 = Base64.encodeBase64String(new byte[] {1, 2, 3});
    CompletableFuture<Response> future = controller.getHouse(user, houseGroupIdBase64);

    // then
    Response response = future.join();
    assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
  }
}
