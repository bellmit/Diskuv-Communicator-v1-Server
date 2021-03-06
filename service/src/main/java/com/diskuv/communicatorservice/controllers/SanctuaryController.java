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
import com.diskuv.communicatorservice.storage.SanctuariesDao;
import com.diskuv.communicatorservice.storage.SanctuaryAttributes;
import com.diskuv.communicatorservice.storage.SanctuaryItem;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.Auth;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.glassfish.jersey.server.ManagedAsync;
import org.signal.storageservice.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Path("/v1/sanctuaries")
public class SanctuaryController {
  private final Logger logger = LoggerFactory.getLogger(SanctuaryController.class);
  private final SanctuariesDao sanctuariesDao;
  private final RateLimiters rateLimiters;
  private final List<UUID> emailAddressesAllowedToDeploySanctuary;

  public SanctuaryController(
      DynamoDbAsyncClient asyncClient,
      DiskuvGroupsConfiguration diskuvGroupsConfiguration,
      RateLimiters rateLimiters) {
    this(
        new SanctuariesDao(asyncClient, diskuvGroupsConfiguration.getSanctuariesTableName()),
        diskuvGroupsConfiguration,
        rateLimiters);
  }

  public SanctuaryController(
      SanctuariesDao sanctuariesDao,
      DiskuvGroupsConfiguration diskuvGroupsConfiguration,
      RateLimiters rateLimiters) {
    this.sanctuariesDao = sanctuariesDao;
    this.emailAddressesAllowedToDeploySanctuary =
        diskuvGroupsConfiguration.getEmailAddressesAllowedToDeploySanctuary().stream()
            .map(DiskuvUuidUtil::uuidForOutdoorEmailAddress)
            .collect(Collectors.toList());
    this.rateLimiters = rateLimiters;
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{sanctuaryGroupId}")
  public CompletableFuture<Response> createSanctuary(
      @Auth User user,
      @PathParam("sanctuaryGroupId") String sanctuaryGroupIdHex,
      @Valid SanctuaryAttributes sanctuaryAttributes) {
    byte[] sanctuaryGroupId;
    try {
      sanctuaryGroupId = Hex.decodeHex(sanctuaryGroupIdHex);
    } catch (DecoderException e) {
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.BAD_REQUEST).build());
    }

    if (!isAllowedToDeploy(user))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.UNAUTHORIZED).build());

    if (rateLimitSanctuary(sanctuaryGroupIdHex))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.TOO_MANY_REQUESTS).build());

    return sanctuariesDao
        .createSanctuary(
            ByteString.copyFrom(sanctuaryGroupId), sanctuaryAttributes.getSupportContactId())
        .thenApply(
            success ->
                Response.status(success ? Response.Status.OK : Response.Status.CONFLICT).build());
  }

  @Timed
  @PATCH
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{sanctuaryGroupId}")
  public CompletableFuture<Response> updateSanctuary(
      @Auth User user,
      @PathParam("sanctuaryGroupId") String sanctuaryGroupIdHex,
      @Valid SanctuaryAttributes sanctuaryAttributes) {
    byte[] sanctuaryGroupId;
    try {
      sanctuaryGroupId = Hex.decodeHex(sanctuaryGroupIdHex);
    } catch (DecoderException e) {
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.BAD_REQUEST).build());
    }

    if (!isAllowedToDeploy(user)) {
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.UNAUTHORIZED).build());
    }

    if (rateLimitSanctuary(sanctuaryGroupIdHex))
      return CompletableFuture.completedFuture(
          Response.status(Response.Status.TOO_MANY_REQUESTS).build());

    ByteString sanctuaryGroup = ByteString.copyFrom(sanctuaryGroupId);
    return sanctuariesDao
        .getSanctuary(sanctuaryGroup)
        .thenApply(
            sanctuaryOpt ->
                sanctuaryOpt.map(
                    sanctuary -> {
                      sanctuary.setSupportContactId(
                          sanctuaryAttributes.getSupportContactId().toString());
                      sanctuary.setSanctuaryEnabled(sanctuaryAttributes.isSanctuaryEnabled());
                      return sanctuary;
                    }))
        .thenCompose(
            sanctuaryOpt ->
                sanctuaryOpt
                    .map(
                        sanctuaryItem ->
                            sanctuariesDao.updateSanctuary(sanctuaryItem).thenApply(unused -> true))
                    .orElse(CompletableFuture.completedFuture(false)))
        .thenApply(
            success ->
                Response.status(success ? Response.Status.OK : Response.Status.NOT_FOUND).build());
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{sanctuaryGroupId}")
  @ManagedAsync
  public void getSanctuary(
      @Auth User user,
      @PathParam("sanctuaryGroupId") String sanctuaryGroupIdHex,
      @Suspended final AsyncResponse asyncResponse) {

    byte[] sanctuaryGroupId;
    try {
      sanctuaryGroupId = Hex.decodeHex(sanctuaryGroupIdHex);
    } catch (DecoderException e) {
      asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST).build());
      return;
    }

    if (rateLimitSanctuary(sanctuaryGroupIdHex)) {
      asyncResponse.resume(Response.status(Response.Status.TOO_MANY_REQUESTS).build());
      return;
    }

    sanctuariesDao
        .getSanctuary(ByteString.copyFrom(sanctuaryGroupId))
        .thenApply(
            sanctuaryItem -> {
              if (sanctuaryItem.isEmpty()) {
                return asyncResponse.resume(Response.status(Response.Status.NOT_FOUND).build());
              }
              SanctuaryItem item = sanctuaryItem.get();
              UUID supportContactId;
              try {
                supportContactId = UUID.fromString(item.getSupportContactId());
              } catch (IllegalArgumentException e) {
                logger.error("The sanctuary " + sanctuaryGroupIdHex + " had a non-UUID support contact id: " + item.getSupportContactId(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
              }
              SanctuaryAttributes attrs =
                  new SanctuaryAttributes(supportContactId, item.isSanctuaryEnabled());
              return asyncResponse.resume(Response.ok(attrs).build());
            })
        .exceptionally(
            e -> {
              logger.warn("Could not give back sanctuary", e);
              return asyncResponse.resume(
                  Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            });
  }

  private boolean rateLimitSanctuary(String sanctuaryGroupIdHex) {
    try {
      rateLimiters.getSanctuaryLookupLimiter().validate(sanctuaryGroupIdHex);
    } catch (RateLimitExceededException e) {
      return true;
    }
    return false;
  }

  private boolean isAllowedToDeploy(User user) {
    return emailAddressesAllowedToDeploySanctuary.contains(user.getUuid());
  }
}
