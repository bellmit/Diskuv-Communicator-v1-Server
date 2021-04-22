package org.signal.storageservice.sanctuaries;

import org.signal.storageservice.storage.protos.groups.Group;

import javax.annotation.Nonnull;
import java.util.Optional;

public class GroupPlusSanctuary {
  private final Group   group;
  private final boolean sanctuary;

  public GroupPlusSanctuary(@Nonnull Optional<Group> group, boolean sanctuary) {
    this.group     = group.orElse(null);
    this.sanctuary = sanctuary;
  }

  public @Nonnull Optional<Group> getGroup() {
    return Optional.ofNullable(group);
  }

  public boolean isSanctuary() {
    return sanctuary;
  }
}
