/*
 * Copyright (C) 2014 Open Whisper Systems
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

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.Anonymous;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAccount;
import org.whispersystems.textsecuregcm.auth.OptionalAccess;
import org.whispersystems.textsecuregcm.entities.PreKey;
import org.whispersystems.textsecuregcm.entities.PreKeyCount;
import org.whispersystems.textsecuregcm.entities.PreKeyResponse;
import org.whispersystems.textsecuregcm.entities.PreKeyResponseItem;
import org.whispersystems.textsecuregcm.entities.PreKeyState;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.KeysDynamoDb;
import org.whispersystems.textsecuregcm.util.Util;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v2/keys")
public class KeysController {

  private final RateLimiters                rateLimiters;
  private final KeysDynamoDb                keysDynamoDb;
  private final org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager accounts;

  private static final String INTERNATIONAL_PREKEY_REQUEST_COUNTER_NAME =
  name(KeysController.class, "internationalPreKeyGet");

  private static final String SOURCE_COUNTRY_TAG_NAME = "sourceCountry";
  private static final String PREKEY_TARGET_IDENTIFIER_TAG_NAME =  "identifierType";

  public KeysController(RateLimiters rateLimiters, KeysDynamoDb keysDynamoDb, org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccountsManager accounts) {
    this.rateLimiters                = rateLimiters;
    this.keysDynamoDb                = keysDynamoDb;
    this.accounts                    = accounts;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PreKeyCount getStatus(@Auth Account account) {
    int count = keysDynamoDb.getCount(account, account.getAuthenticatedDevice().get().getId());

    if (count > 0) {
      count = count - 1;
    }

    return new PreKeyCount(count);
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public void setKeys(@Auth DisabledPermittedAccount disabledPermittedAccount, @Valid PreKeyState preKeys)  {
    Account account           = disabledPermittedAccount.getAccount();
    Device  device            = account.getAuthenticatedDevice().get();
    boolean updateAccount     = false;

    if (!preKeys.getSignedPreKey().equals(device.getSignedPreKey())) {
      device.setSignedPreKey(preKeys.getSignedPreKey());
      updateAccount = true;
    }

    if (!preKeys.getIdentityKey().equals(account.getIdentityKey())) {
      account.setIdentityKey(preKeys.getIdentityKey());
      updateAccount = true;
    }

    if (updateAccount) {
      accounts.update(account);
    }

    keysDynamoDb.store(account, device.getId(), preKeys.getPreKeys());
  }

  @Timed
  @GET
  @Path("/{identifier}/{device_id}")
  @Produces(MediaType.APPLICATION_JSON)
  public Optional<PreKeyResponse> getDeviceKeys(@Auth                                     Account realAccount,
                                                @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
                                                @PathParam("identifier")                  AmbiguousIdentifier targetName,
                                                @PathParam("device_id")                   String deviceId)
      throws RateLimitExceededException
  {
    // Unlike Signal, we expect every API to fully authenticate the real source, and edge routers are going to authenticate
    // way before it gets to the Java server. Those edge routers make it possible to stop denial of service.
    // However, it is perfectly fine if we treat the effective account as unknown from this point onwards; that will
    // force, among other things, a validation of the anonymous access key.
    Optional<Account> account = accessKey.isPresent() ? Optional.empty() : Optional.of(realAccount);

    if (!account.isPresent() && !accessKey.isPresent()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    if (!targetName.hasUuid()) {
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount target = accounts.get(targetName.getUuid());

    OptionalAccess.verify(account, accessKey, target, deviceId);

    if (account.isPresent()) {
      // Diskuv Change: We do not include the validated target phone number in the rate limit, since we may not
      // have a target rate number (we send responses regardless if there is an account)
      rateLimiters.getPreKeysLimiter().validate(account.get().getNumber() + "__" + targetName + "." + deviceId);
    }

    Map<Long, PreKey>        preKeysByDeviceId = getLocalKeys(target, deviceId);
    List<PreKeyResponseItem> responseItems     = new LinkedList<>();

    for (org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticDevice device : target.getDevices()) {
      if (device.isEnabled() && (deviceId.equals("*") || device.getId() == Long.parseLong(deviceId))) {
        SignedPreKey signedPreKey = device.getSignedPreKey();
        PreKey       preKey       = preKeysByDeviceId.get(device.getId());

        if (signedPreKey != null || preKey != null) {
          responseItems.add(new PreKeyResponseItem(device.getId(), device.getRegistrationId(), signedPreKey, preKey));
        }
      }
    }

    if (responseItems.isEmpty()) return Optional.empty();
    else                         return Optional.of(new PreKeyResponse(target.getIdentityKey(), responseItems));
  }

  @Timed
  @PUT
  @Path("/signed")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setSignedKey(@Auth Account account, @Valid SignedPreKey signedPreKey) {
    Device  device            = account.getAuthenticatedDevice().get();

    device.setSignedPreKey(signedPreKey);
    accounts.update(account);
  }

  @Timed
  @GET
  @Path("/signed")
  @Produces(MediaType.APPLICATION_JSON)
  public Optional<SignedPreKey> getSignedKey(@Auth Account account) {
    Device       device       = account.getAuthenticatedDevice().get();
    SignedPreKey signedPreKey = device.getSignedPreKey();

    if (signedPreKey != null) return Optional.of(signedPreKey);
    else                      return Optional.empty();
  }

  private Map<Long, PreKey> getLocalKeys(org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount possibleDestination, String deviceIdSelector) {
    // Special handling for synthetic accounts
    if (possibleDestination.getRealAccount().isEmpty()) {
      if (deviceIdSelector.equals("*")) {
        return possibleDestination.getDevices().stream()
            .map(device -> new org.whispersystems.textsecuregcm.util.Pair<>(device.getId(), device.generateUniqueSyntheticPreKey().get()))
            .collect(Collectors.toMap(pair -> pair.first(), pair -> pair.second()));
      }

      long deviceId = Long.parseLong(deviceIdSelector);
      return possibleDestination.getDevice(deviceId)
          .map(device -> Map.of(deviceId, device.generateUniqueSyntheticPreKey().get()))
          .orElse(Collections.emptyMap());
    }

    Account destination = possibleDestination.getRealAccount().get();

    try {
      if (deviceIdSelector.equals("*")) {
        return keysDynamoDb.take(destination);
      }

      long deviceId = Long.parseLong(deviceIdSelector);

      return keysDynamoDb.take(destination, deviceId)
              .map(preKey -> Map.of(deviceId, preKey))
              .orElse(Collections.emptyMap());
    } catch (NumberFormatException e) {
      throw new WebApplicationException(Response.status(422).build());
    }
  }
}
