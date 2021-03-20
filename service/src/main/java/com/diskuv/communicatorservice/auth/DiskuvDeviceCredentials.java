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
package com.diskuv.communicatorservice.auth;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/** For {@literal @Auth} account. */
public class DiskuvDeviceCredentials {
  @Nonnull private final String bearerToken;
  private final long deviceId;
  @Nonnull private final byte[] devicePassword;

  public DiskuvDeviceCredentials(
      @Nonnull String bearerToken, long deviceId, @Nonnull byte[] devicePassword) {
    this.bearerToken = bearerToken;
    this.deviceId = deviceId;
    this.devicePassword = devicePassword;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DiskuvDeviceCredentials that = (DiskuvDeviceCredentials) o;
    return deviceId == that.deviceId
        && bearerToken.equals(that.bearerToken)
        && Arrays.equals(devicePassword, that.devicePassword);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(bearerToken, deviceId);
    result = 31 * result + Arrays.hashCode(devicePassword);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DiskuvDeviceCredentials.class.getSimpleName() + "[", "]")
        .add("bearerToken='" + bearerToken + "'")
        .add("deviceId=" + deviceId)
        .add("devicePassword=" + Arrays.toString(devicePassword))
        .toString();
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public long getDeviceId() {
    return deviceId;
  }

  public byte[] getDevicePassword() {
    return devicePassword;
  }
}
