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

import org.signal.storageservice.storage.protos.groups.GroupChanges;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

class CachedGroupChanges {
  private final List<GroupChanges.GroupChangeState> groupChanges;
  private Integer lastGroupVersion;

  public CachedGroupChanges() {
    this.groupChanges = new ArrayList<>();
  }

  public @Nullable Integer getLastGroupVersion() {
    return lastGroupVersion;
  }

  public List<GroupChanges.GroupChangeState> getGroupChanges() {
    return groupChanges;
  }

  public void add(int groupVersion, GroupChanges.GroupChangeState changeState) {
    lastGroupVersion = groupVersion;
    groupChanges.add(changeState);
  }
}
