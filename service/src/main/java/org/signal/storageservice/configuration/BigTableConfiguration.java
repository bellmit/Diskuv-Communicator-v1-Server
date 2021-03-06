/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotEmpty;

public class BigTableConfiguration {

  @JsonProperty
  @NotEmpty
  private String projectId;

  @JsonProperty
  @NotEmpty
  private String instanceId;

  /*
    [Diskuv Change] Import of groups from storage-service. The following properties are not used in Groups.

    @JsonProperty
    @NotEmpty
    private String clusterId;

    @JsonProperty
    @NotEmpty
    private String contactManifestsTableId;

    @JsonProperty
    @NotEmpty
    private String contactsTableId;
  */

  @JsonProperty
  @NotEmpty
  private String groupsTableId;

  @JsonProperty
  @NotEmpty
  private String groupLogsTableId;


  public String getProjectId() {
    return projectId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  /*
   [Diskuv Change] Import of groups from storage-service. The following properties are not used in Groups.

   public String getClusterId() {
     return clusterId;
   }

   public String getContactManifestsTableId() {
     return contactManifestsTableId;
   }

   public String getContactsTableId() {
     return contactsTableId;
   }
  */

  public String getGroupsTableId() {
    return groupsTableId;
  }

  public String getGroupLogsTableId() {
    return groupLogsTableId;
  }

  // [Diskuv Change] Add setter for programmatic config
  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  // [Diskuv Change] Add setter for programmatic config
  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  // [Diskuv Change] Add setter for programmatic config
  public void setGroupsTableId(String groupsTableId) {
    this.groupsTableId = groupsTableId;
  }

  // [Diskuv Change] Add setter for programmatic config
  public void setGroupLogsTableId(String groupLogsTableId) {
    this.groupLogsTableId = groupLogsTableId;
  }
}
