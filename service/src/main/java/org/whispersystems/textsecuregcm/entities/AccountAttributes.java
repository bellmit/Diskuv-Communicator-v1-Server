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
package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import javax.validation.constraints.Size;
import org.whispersystems.textsecuregcm.storage.Device.DeviceCapabilities;
import org.whispersystems.textsecuregcm.storage.PaymentAddress;

public class AccountAttributes {

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private int registrationId;

  @JsonProperty
  @Size(max = 204, message = "This field must be less than 50 characters")
  private String name;

  @JsonProperty
  private String pin;

  @JsonProperty
  private String registrationLock;

  @JsonProperty
  private byte[] unidentifiedAccessKey;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private List<PaymentAddress> payments;

  @JsonProperty
  private DeviceCapabilities capabilities;

  @JsonProperty
  private boolean discoverableByPhoneNumber = true;

  public AccountAttributes() {}

  @VisibleForTesting
  public AccountAttributes(boolean fetchesMessages, int registrationId, String pin) {
    this(fetchesMessages, registrationId, null, pin, null, null, true, null);
  }

  @VisibleForTesting
  public AccountAttributes(boolean fetchesMessages, int registrationId, String name, String pin, String registrationLock, List<PaymentAddress> payments, boolean discoverableByPhoneNumber, final DeviceCapabilities capabilities) {
    this.fetchesMessages           = fetchesMessages;
    this.registrationId            = registrationId;
    this.name                      = name;
    this.pin                       = pin;
    this.registrationLock          = registrationLock;
    this.payments                  = payments;
    this.discoverableByPhoneNumber = discoverableByPhoneNumber;
    this.capabilities              = capabilities;
  }

  public boolean getFetchesMessages() {
    return fetchesMessages;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public String getName() {
    return name;
  }

  public String getPin() {
    return pin;
  }

  public void setPin(String pin) {
    this.pin = pin;
  }

  public String getRegistrationLock() {
    return registrationLock;
  }

  public byte[] getUnidentifiedAccessKey() {
    return unidentifiedAccessKey;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public DeviceCapabilities getCapabilities() {
    return capabilities;
  }

  public List<PaymentAddress> getPayments() {
    return payments;
  }

  public boolean isDiscoverableByPhoneNumber() {
    return discoverableByPhoneNumber;
  }
}
