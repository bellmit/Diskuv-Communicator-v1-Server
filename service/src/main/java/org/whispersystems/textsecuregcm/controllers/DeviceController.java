/*
 * Copyright (C) 2013-2018 Open WhisperSystems
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

import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.auth.InvalidAuthorizationHeaderException;
import org.whispersystems.textsecuregcm.auth.StoredVerificationCode;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.DeviceInfo;
import org.whispersystems.textsecuregcm.entities.DeviceInfoList;
import org.whispersystems.textsecuregcm.entities.DeviceResponse;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.Device.DeviceCapabilities;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.PendingDevicesManager;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.util.VerificationCode;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.dropwizard.auth.Auth;
import org.whispersystems.textsecuregcm.util.ua.ClientPlatform;
import org.whispersystems.textsecuregcm.util.ua.UnrecognizedUserAgentException;
import org.whispersystems.textsecuregcm.util.ua.UserAgent;
import org.whispersystems.textsecuregcm.util.ua.UserAgentUtil;

import static com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.DEVICE_AUTHORIZATION_HEADER;

@Path("/v1/devices")
public class DeviceController {

  private final Logger logger = LoggerFactory.getLogger(DeviceController.class);

  public static final int MAX_DEVICES = 6;

  private final PendingDevicesManager pendingDevices;
  private final AccountsManager       accounts;
  private final com.diskuv.communicatorservice.auth.JwtAuthentication     jwtAuthentication;
  private final MessagesManager       messages;
  private final RateLimiters          rateLimiters;
  private final Map<String, Integer>  maxDeviceConfiguration;

  public DeviceController(PendingDevicesManager pendingDevices,
                          AccountsManager accounts,
                          com.diskuv.communicatorservice.auth.JwtAuthentication jwtAuthentication,
                          MessagesManager messages,
                          RateLimiters rateLimiters,
                          Map<String, Integer> maxDeviceConfiguration)
  {
    this.pendingDevices         = pendingDevices;
    this.accounts               = accounts;
    this.jwtAuthentication      = jwtAuthentication;
    this.messages               = messages;
    this.rateLimiters           = rateLimiters;
    this.maxDeviceConfiguration = maxDeviceConfiguration;
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public DeviceInfoList getDevices(@Auth Account account) {
    List<DeviceInfo> devices = new LinkedList<>();

    for (Device device : account.getDevices()) {
      devices.add(new DeviceInfo(device.getId(), device.getName(),
                                 device.getLastSeen(), device.getCreated()));
    }

    return new DeviceInfoList(devices);
  }

  @Timed
  @DELETE
  @Path("/{device_id}")
  public void removeDevice(@Auth Account account, @PathParam("device_id") long deviceId) {
    if (account.getAuthenticatedDevice().get().getId() != Device.MASTER_ID) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    account.removeDevice(deviceId);
    accounts.update(account);
    messages.clear(account.getNumber(), account.getUuid(), deviceId);
  }

  @Timed
  @GET
  @Path("/provisioning/code")
  @Produces(MediaType.APPLICATION_JSON)
  public VerificationCode createDeviceToken(@Auth Account account)
      throws RateLimitExceededException, DeviceLimitExceededException
  {
    UUID accountUuid = account.getUuid();
    String accountId = accountUuid.toString();
    rateLimiters.getAllocateDeviceLimiter().validate(accountId);

    int maxDeviceLimit = MAX_DEVICES;

    if (maxDeviceConfiguration.containsKey(accountId)) {
      maxDeviceLimit = maxDeviceConfiguration.get(accountId);
    }

    if (account.getEnabledDeviceCount() >= maxDeviceLimit) {
      throw new DeviceLimitExceededException(account.getDevices().size(), MAX_DEVICES);
    }

    if (account.getAuthenticatedDevice().get().getId() != Device.MASTER_ID) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    VerificationCode       verificationCode       = generateVerificationCode();
    StoredVerificationCode storedVerificationCode = new StoredVerificationCode(verificationCode.getVerificationCode(),
                                                                               System.currentTimeMillis(),
                                                                               null);

    pendingDevices.store(accountUuid, storedVerificationCode);

    return verificationCode;
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{verification_code}")
  public DeviceResponse verifyDeviceToken(@PathParam("verification_code") String verificationCode,
                                          @HeaderParam("Authorization")   String authorizationHeader,
                                          @HeaderParam(DEVICE_AUTHORIZATION_HEADER)   String deviceAuthorizationHeader,
                                          @HeaderParam("User-Agent")      String userAgent,
                                          @Valid                          AccountAttributes accountAttributes)
      throws RateLimitExceededException, DeviceLimitExceededException
  {
    // account authentication
    AuthHeaderSupport.validateJwtAndGetOutdoorsUUID(jwtAuthentication, authorizationHeader);

    // device password to be used for subsequent device authentication.
    // ignore any device id from the device header though. we will create the "next" device id a bit later in this method
    final com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader deviceHeader;
    try {
      deviceHeader = com.diskuv.communicatorservice.auth.DeviceAuthorizationHeader.fromFullHeader(deviceAuthorizationHeader);
    } catch (InvalidAuthorizationHeaderException e) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    byte[] devicePassword      = deviceHeader.getDevicePassword();
    UUID accountUuid = deviceHeader.getAccountId();
    String accountId = accountUuid.toString();

    rateLimiters.getVerifyDeviceLimiter().validate(accountId);

    Optional<StoredVerificationCode> storedVerificationCode = pendingDevices.getCodeForPendingDevice(accountUuid);

    if (!storedVerificationCode.isPresent() || !storedVerificationCode.get().isValid(verificationCode)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }

    Optional<Account> account = accounts.get(accountUuid);
    if (account.isEmpty()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    final DeviceCapabilities capabilities = accountAttributes.getCapabilities();
    if (capabilities != null && isCapabilityDowngrade(account.get(), capabilities, userAgent)) {
      throw new WebApplicationException(Response.status(409).build());
    }

    Device device = new Device();
    device.setName(accountAttributes.getName());
    device.setAuthenticationCredentials(new AuthenticationCredentials(devicePassword));
    device.setSignalingKey(accountAttributes.getSignalingKey());
    device.setFetchesMessages(accountAttributes.getFetchesMessages());
    device.setId(account.get().getNextDeviceId());
    device.setRegistrationId(accountAttributes.getRegistrationId());
    device.setLastSeen(Util.todayInMillis());
    device.setCreated(System.currentTimeMillis());

    account.get().addDevice(device);
    messages.clear(accountId, account.get().getUuid(), device.getId());
    accounts.update(account.get());

    pendingDevices.remove(accountUuid);

    return new DeviceResponse(device.getId());
  }

  @Timed
  @PUT
  @Path("/unauthenticated_delivery")
  public void setUnauthenticatedDelivery(@Auth Account account) {
    assert(account.getAuthenticatedDevice().isPresent());
    // Deprecated
  }

  @Timed
  @PUT
  @Path("/capabilities")
  public void setCapabiltities(@Auth Account account, @Valid DeviceCapabilities capabilities) {
    assert(account.getAuthenticatedDevice().isPresent());
    account.getAuthenticatedDevice().get().setCapabilities(capabilities);
    accounts.update(account);
  }

  private UUID getAndValidateAccountUuid(String authorizationHeader) {
    final String bearerToken;
    try {
      bearerToken = com.diskuv.communicatorservice.auth.BearerTokenAuthorizationHeader.fromFullHeader(authorizationHeader);
    } catch (InvalidAuthorizationHeaderException e) {
      logger.info("Bad Authorization Header", e);
      throw new WebApplicationException(Response.status(401).build());
    }
    final String emailAddress;
    try {
      emailAddress = jwtAuthentication.verifyBearerTokenAndGetEmailAddress(bearerToken);
    } catch (IllegalArgumentException e) {
      throw new WebApplicationException(Response.status(401).build());
    }
    return DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress);
  }

  @VisibleForTesting protected VerificationCode generateVerificationCode() {
    SecureRandom random = new SecureRandom();
    int randomInt       = 100000 + random.nextInt(900000);
    return new VerificationCode(randomInt);
  }

  private boolean isCapabilityDowngrade(Account account, DeviceCapabilities capabilities, String userAgent) {
    boolean isDowngrade = false;

    if (account.isGroupsV2Supported()) {
      try {
        switch (UserAgentUtil.parseUserAgentString(userAgent).getPlatform()) {
          case DESKTOP:
          case ANDROID: {
            if (!capabilities.isGv2_3()) {
              isDowngrade = true;
            }

            break;
          }

          case IOS: {
            if (!capabilities.isGv2_2() && !capabilities.isGv2_3()) {
              isDowngrade = true;
            }

            break;
          }
        }
      } catch (final UnrecognizedUserAgentException e) {
        // If we can't parse the UA string, the client is for sure too old to support groups V2
        isDowngrade = true;
      }
    }

    return isDowngrade;
  }
}
