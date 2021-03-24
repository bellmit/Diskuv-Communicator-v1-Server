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
import java.util.Objects;
import java.util.StringJoiner;

/** For {@literal @Auth} user or {@literal @Auth} group. Eventually {@literal @Auth} sanctuary. */
public class DiskuvRoleCredentials {
  @Nonnull private final String bearerToken;
  @Nonnull private final String username;
  @Nonnull private final String password;

  public DiskuvRoleCredentials(
          @Nonnull String bearerToken, @Nonnull String username, @Nonnull String password) {
    this.bearerToken = bearerToken;
    this.username = username;
    this.password = password;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DiskuvRoleCredentials that = (DiskuvRoleCredentials) o;
    return bearerToken.equals(that.bearerToken)
        && username.equals(that.username)
        && password.equals(that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bearerToken, username, password);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DiskuvRoleCredentials.class.getSimpleName() + "[", "]")
        .add("bearerToken='" + bearerToken + "'")
        .add("username='" + username + "'")
        .add("password='" + password + "'")
        .toString();
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
