// Copyright 2021 Diskuv, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.diskuv.communicatorservice.controllers;

import com.codahale.metrics.annotation.Timed;
import com.diskuv.communicatorservice.storage.HouseAttributes;
import com.diskuv.communicatorservice.storage.HousesDao;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.Auth;
import org.apache.commons.codec.binary.Base64;
import org.signal.storageservice.auth.User;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Path("/v1/houses")
public class HouseController {
  private final HousesDao housesDao;
  private final RateLimiters rateLimiters;
  private final List<UUID> emailAddressesAllowedToDeployHouse;

  public HouseController(
      DynamoDbAsyncClient asyncClient,
      DiskuvGroupsConfiguration diskuvGroupsConfiguration,
      RateLimiters rateLimiters) {
    this(
        new HousesDao(asyncClient, diskuvGroupsConfiguration.getHouseTableName()),
        diskuvGroupsConfiguration,
        rateLimiters);
  }

  public HouseController(
      HousesDao housesDao,
      DiskuvGroupsConfiguration diskuvGroupsConfiguration,
      RateLimiters rateLimiters) {
    this.housesDao = housesDao;
    this.emailAddressesAllowedToDeployHouse =
        diskuvGroupsConfiguration.getEmailAddressesAllowedToDeployHouse().stream()
            .map(emailAddress -> DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress))
            .collect(Collectors.toList());
    this.rateLimiters = rateLimiters;
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{houseGroupId}")
  public CompletableFuture<Response> createHouse(
      @Auth User user,
      @PathParam("houseGroupId") String houseGroupIdBase64,
      @Valid HouseAttributes houseAttributes) {
    byte[] houseGroupId = Base64.decodeBase64(houseGroupIdBase64);
    if (!isAllowedToDeploy(user))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.UNAUTHORIZED).build());

    if (rateLimitHouse(houseGroupIdBase64))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.TOO_MANY_REQUESTS).build());

    return housesDao
        .createHouse(ByteString.copyFrom(houseGroupId), houseAttributes.getSupportContactId())
        .thenApply(
            success ->
                Response.status(success ? Response.Status.OK : Response.Status.CONFLICT).build());
  }

  @Timed
  @PATCH
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{houseGroupId}")
  public CompletableFuture<Response> updateHouse(
      @Auth User user,
      @PathParam("houseGroupId") String houseGroupIdBase64,
      @Valid HouseAttributes houseAttributes) {
    byte[] houseGroupId = Base64.decodeBase64(houseGroupIdBase64);
    if (!isAllowedToDeploy(user)) {
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.UNAUTHORIZED).build());
    }

    if (rateLimitHouse(houseGroupIdBase64))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.TOO_MANY_REQUESTS).build());

    ByteString houseGroup = ByteString.copyFrom(houseGroupId);
    return housesDao
        .getHouse(houseGroup)
        .thenApply(
            houseOpt ->
                houseOpt.map(
                    house -> {
                      house.setSupportContactId(houseAttributes.getSupportContactId().toString());
                      house.setHouseEnabled(houseAttributes.isHouseEnabled());
                      return house;
                    }))
        .thenCompose(
            houseOpt ->
                houseOpt
                    .map(houseItem -> housesDao.updateHouse(houseItem).thenApply(unused -> true))
                    .orElse(CompletableFuture.completedFuture(false)))
        .thenApply(
            success ->
                Response.status(success ? Response.Status.OK : Response.Status.NOT_FOUND).build());
  }

  @Timed
  @GET
  @Path("/{houseGroupId}")
  public CompletableFuture<Response> getHouse(
      @Auth User user, @PathParam("houseGroupId") String houseGroupIdBase64) {
    byte[] houseGroupId;
    try {
      houseGroupId = Base64.decodeBase64(houseGroupIdBase64);
    } catch (IllegalArgumentException e) {
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.BAD_REQUEST).build());
    }

    if (rateLimitHouse(houseGroupIdBase64))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.TOO_MANY_REQUESTS).build());

    return housesDao
        .getHouse(ByteString.copyFrom(houseGroupId))
        .thenApply(
            houseItem -> {
              if (houseItem.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
              }
              return Response.ok().build();
            });
  }

  private boolean rateLimitHouse(String houseGroupIdBase64) {
    try {
      rateLimiters.getHouseLookupLimiter().validate(houseGroupIdBase64);
    } catch (RateLimitExceededException e) {
      return true;
    }
    return false;
  }

  private boolean isAllowedToDeploy(User user) {
    return emailAddressesAllowedToDeployHouse.contains(user.getUuid());
  }
}
