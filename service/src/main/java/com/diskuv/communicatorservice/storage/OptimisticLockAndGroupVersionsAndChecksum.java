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
package com.diskuv.communicatorservice.storage;

class OptimisticLockAndGroupVersionsAndChecksum {
  private final int optimisticLockVersion;
  private final int groupVersion;
  private final byte[] checksum;

  public OptimisticLockAndGroupVersionsAndChecksum(
      int optimisticLockVersion, int groupVersion, byte[] checksum) {
    this.optimisticLockVersion = optimisticLockVersion;
    this.groupVersion = groupVersion;
    this.checksum = checksum;
  }

  public int getOptimisticLockVersion() {
    return optimisticLockVersion;
  }

  public int getGroupVersion() {
    return groupVersion;
  }

  public byte[] getChecksum() {
    return checksum;
  }
}
