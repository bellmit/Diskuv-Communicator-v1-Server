/*
 * Copyright (C) 2013 Open WhisperSystems
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
package org.whispersystems.textsecuregcm.storage;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.security.Principal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.security.auth.Subject;
import org.whispersystems.textsecuregcm.auth.AmbiguousIdentifier;
import org.whispersystems.textsecuregcm.auth.StoredRegistrationLock;

public class Account implements Principal, org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount {

  @JsonIgnore
  private UUID uuid;

  @JsonProperty
  private String number;

  @JsonProperty
  private Set<Device> devices = new HashSet<>();

  @JsonProperty
  private String identityKey;

  @JsonProperty("cpv")
  private String currentProfileVersion;

  @JsonProperty
  private String name;

  @JsonProperty
  private String emailAddress;

  @JsonProperty
  private String avatar;

  @JsonProperty
  private String pin;

  @JsonProperty
  private String registrationLock;

  @JsonProperty
  private String registrationLockSalt;

  @JsonProperty("uak")
  private byte[] unidentifiedAccessKey;

  @JsonProperty("uua")
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty("inCds")
  private boolean discoverableByPhoneNumber = true;

  @JsonProperty("_ddbV")
  private int dynamoDbMigrationVersion;

  @JsonIgnore
  private Device authenticatedDevice;

  @JsonIgnore
  private UUID authenticatedOutdoorsUuid;

  public Account() {}

  @VisibleForTesting
  public Account(UUID uuid, Set<Device> devices, byte[] unidentifiedAccessKey) {
    this.number                = "";
    this.uuid                  = uuid;
    this.devices               = devices;
    this.unidentifiedAccessKey = unidentifiedAccessKey;
  }

  @VisibleForTesting
  public Account(String number, UUID uuid, Set<Device> devices, byte[] unidentifiedAccessKey) {
    this.number                = number;
    this.uuid                  = uuid;
    this.devices               = devices;
    this.unidentifiedAccessKey = unidentifiedAccessKey;
  }

  @Override
  public Optional<Account> getRealAccount() {
    return Optional.of(this);
  }

  public Optional<Device> getAuthenticatedDevice() {
    return Optional.ofNullable(authenticatedDevice);
  }

  public void setAuthenticatedDevice(Device device) {
    this.authenticatedDevice = device;
  }

  public UUID getAuthenticatedOutdoorsUuid() {
    return authenticatedOutdoorsUuid;
  }

  public void setAuthenticatedOutdoorsUuid(final UUID authenticatedOutdoorsUuid) {
    this.authenticatedOutdoorsUuid = authenticatedOutdoorsUuid;
  }

  public UUID getUuid() {
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  /**
   * To be compatible with the Account API, we accept the API call but always
   * store an empty string.
   * That will cause both Redis and the PostgreSQL databases to store
   * an empty string via {@link AccountsManager#update(Account)}.
   */
  public void setNumber(String number) {
    this.number = "";
  }

  /**
   * This returns the UUID. This makes it easy to be compatible with the bulk of the original Signal server
   * code, which expects a phone number.
   */
  public String getNumber() {
    return uuid.toString();
  }

  public void addDevice(Device device) {
    this.devices.remove(device);
    this.devices.add(device);
  }

  public void removeDevice(long deviceId) {
    this.devices.remove(new Device(deviceId, null, null, null, null, null, null, false, 0, null, 0, 0, "NA", 0, null));
  }

  public Set<Device> getDevices() {
    return devices;
  }

  public Optional<Device> getMasterDevice() {
    return getDevice(Device.MASTER_ID);
  }

  public Optional<Device> getDevice(long deviceId) {
    for (Device device : devices) {
      if (device.getId() == deviceId) {
        return Optional.of(device);
      }
    }

    return Optional.empty();
  }

  public boolean isGroupsV2Supported() {
    return devices.stream()
                  .filter(Device::isEnabled)
                  .allMatch(Device::isGroupsV2Supported);
  }

  public boolean isStorageSupported() {
    // The storage service is not used in Diskuv, so storage "supported" is always false
    return false;
  }

  public boolean isTransferSupported() {
    return getMasterDevice().map(Device::getCapabilities).map(Device.DeviceCapabilities::isTransfer).orElse(false);
  }

  public boolean isGv1MigrationSupported() {
    return devices.stream()
                  .filter(Device::isEnabled)
                  .allMatch(device -> device.getCapabilities() != null && device.getCapabilities().isGv1Migration());
  }

  public boolean isEnabled() {
    return getMasterDevice().map(Device::isEnabled).orElse(false);
  }

  public long getNextDeviceId() {
    long highestDevice = Device.MASTER_ID;

    for (Device device : devices) {
      if (!device.isEnabled()) {
        return device.getId();
      } else if (device.getId() > highestDevice) {
        highestDevice = device.getId();
      }
    }

    return highestDevice + 1;
  }

  public int getEnabledDeviceCount() {
    int count = 0;

    for (Device device : devices) {
      if (device.isEnabled()) count++;
    }

    return count;
  }

  public boolean isRateLimited() {
    return true;
  }

  public Optional<String> getRelay() {
    return Optional.empty();
  }

  public void setIdentityKey(String identityKey) {
    this.identityKey = identityKey;
  }

  public String getIdentityKey() {
    return identityKey;
  }

  public long getLastSeen() {
    long lastSeen = 0;

    for (Device device : devices) {
      if (device.getLastSeen() > lastSeen) {
        lastSeen = device.getLastSeen();
      }
    }

    return lastSeen;
  }

  public Optional<String> getCurrentProfileVersion() {
    return Optional.ofNullable(currentProfileVersion);
  }

  public void setCurrentProfileVersion(String currentProfileVersion) {
    this.currentProfileVersion = currentProfileVersion;
  }

  public String getProfileName() {
    return name;
  }

  public void setProfileName(String name) {
    this.name = name;
  }

  public String getProfileEmailAddress() {
    return emailAddress;
  }

  public void setProfileEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }

  public String getPin() {
    return pin;
  }

  public void setPin(String pin) {
    this.pin = pin;
  }

  public void setRegistrationLock(String registrationLock, String registrationLockSalt) {
    this.registrationLock     = registrationLock;
    this.registrationLockSalt = registrationLockSalt;
  }

  public StoredRegistrationLock getRegistrationLock() {
    return new StoredRegistrationLock(Optional.ofNullable(registrationLock), Optional.ofNullable(registrationLockSalt), Optional.ofNullable(pin), getLastSeen());
  }

  public Optional<byte[]> getUnidentifiedAccessKey() {
    return Optional.ofNullable(unidentifiedAccessKey);
  }

  public void setUnidentifiedAccessKey(byte[] unidentifiedAccessKey) {
    this.unidentifiedAccessKey = unidentifiedAccessKey;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public void setUnrestrictedUnidentifiedAccess(boolean unrestrictedUnidentifiedAccess) {
    this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
  }

  public boolean isFor(AmbiguousIdentifier identifier) {
    if      (identifier.hasUuid())   return identifier.getUuid().equals(uuid);
    else if (identifier.hasNumber()) return identifier.getNumber().equals(number);
    else                             throw new AssertionError();
  }

  public boolean isDiscoverableByPhoneNumber() {
    return this.discoverableByPhoneNumber;
  }

  public void setDiscoverableByPhoneNumber(final boolean discoverableByPhoneNumber) {
    this.discoverableByPhoneNumber = discoverableByPhoneNumber;
  }

  public int getDynamoDbMigrationVersion() {
    return dynamoDbMigrationVersion;
  }

  public void setDynamoDbMigrationVersion(int dynamoDbMigrationVersion) {
    this.dynamoDbMigrationVersion = dynamoDbMigrationVersion;
  }

  // Principal implementation

  @Override
  @JsonIgnore
  public String getName() {
    return null;
  }

  @Override
  @JsonIgnore
  public boolean implies(Subject subject) {
    return false;
  }
}
