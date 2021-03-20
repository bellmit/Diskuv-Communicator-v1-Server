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
package com.diskuv.communicatorservice.storage.command;

import com.diskuv.communicatorservice.storage.GroupsDao;
import com.diskuv.communicatorservice.storage.clients.AwsClientFactory;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

public class CreateTableGroupsCommand extends BaseCreateTableCommand {
  public CreateTableGroupsCommand() {
    super("creategroupstable", "Create the Groups DynamoDB table");
  }

  @Override
  protected void run(
      Bootstrap<WhisperServerConfiguration> bootstrap,
      Namespace namespace,
      WhisperServerConfiguration configuration)
      throws Exception {
    DiskuvGroupsConfiguration config = customizeConfiguration(namespace, configuration);

    // setup ddb
    DynamoDbAsyncClient dbAsyncClient = new AwsClientFactory(config).getDynamoDbAsyncClient();
    GroupsDao groupsDao =
        new GroupsDao(dbAsyncClient, config.getGroupsTableName(), config.getChecksumSharedKey());

    // create table
    createTable(groupsDao.getTable());
  }
}
