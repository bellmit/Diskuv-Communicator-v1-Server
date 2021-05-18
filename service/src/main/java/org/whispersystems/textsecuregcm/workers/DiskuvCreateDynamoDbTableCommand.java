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
package org.whispersystems.textsecuregcm.workers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.diskuv.communicatorservice.storage.clients.AwsClientFactory;
import com.diskuv.communicatorservice.storage.configuration.DiskuvAwsCredentialsType;
import com.diskuv.communicatorservice.storage.configuration.DiskuvDynamoDBConfiguration;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.storage.AccountsDynamoDb;
import org.whispersystems.textsecuregcm.storage.KeysDynamoDb;
import org.whispersystems.textsecuregcm.storage.MessagesDynamoDb;
import org.whispersystems.textsecuregcm.storage.MigrationDeletedAccounts;
import org.whispersystems.textsecuregcm.storage.MigrationRetryAccounts;

public class DiskuvCreateDynamoDbTableCommand extends ConfiguredCommand<WhisperServerConfiguration> {

  private final Logger logger = LoggerFactory.getLogger(DiskuvCreateDynamoDbTableCommand.class);

  public DiskuvCreateDynamoDbTableCommand() {
    super("migratedynamodbtables",
        "Create and upgrade DynamoDB tables for the signal-server, but not the tables for the storage service (ex. group tables). "
            + "Upgrades are not yet needed and have not been implemented, but are reserved for adding new global secondary indexes");
  }

  @Override
  protected void run(
      Bootstrap<WhisperServerConfiguration> bootstrap,
      Namespace namespace,
      WhisperServerConfiguration configuration)
      throws Exception {

    // Messages
    createTableAndWait(configuration, namespace, WhisperServerConfiguration::getMessageDynamoDbConfiguration,
        new CreateTableRequest()
            .withTableName(configuration.getMessageDynamoDbConfiguration().getTableName())
            .withBillingMode(BillingMode.PAY_PER_REQUEST)
            .withAttributeDefinitions(
                new AttributeDefinition(MessagesDynamoDb.KEY_PARTITION, ScalarAttributeType.B),
                new AttributeDefinition(MessagesDynamoDb.KEY_SORT, ScalarAttributeType.B),
                new AttributeDefinition(MessagesDynamoDb.LOCAL_INDEX_MESSAGE_UUID_KEY_SORT, ScalarAttributeType.B)
            )
            .withKeySchema(
                new KeySchemaElement(MessagesDynamoDb.KEY_PARTITION, KeyType.HASH),
                new KeySchemaElement(MessagesDynamoDb.KEY_SORT, KeyType.RANGE)
            )
            .withLocalSecondaryIndexes(
                new LocalSecondaryIndex().withIndexName(MessagesDynamoDb.LOCAL_INDEX_MESSAGE_UUID_NAME)
                    .withKeySchema(
                        new KeySchemaElement(MessagesDynamoDb.KEY_PARTITION, KeyType.HASH),
                        new KeySchemaElement(MessagesDynamoDb.LOCAL_INDEX_MESSAGE_UUID_KEY_SORT, KeyType.RANGE))
                    .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY))
            )
    );

    // Keys
    createTableAndWait(configuration, namespace, WhisperServerConfiguration::getKeysDynamoDbConfiguration,
        new CreateTableRequest()
            .withTableName(configuration.getKeysDynamoDbConfiguration().getTableName())
            .withBillingMode(BillingMode.PAY_PER_REQUEST)
            .withAttributeDefinitions(
                new AttributeDefinition(KeysDynamoDb.KEY_ACCOUNT_UUID, ScalarAttributeType.B),
                new AttributeDefinition(KeysDynamoDb.KEY_DEVICE_ID_KEY_ID, ScalarAttributeType.B)
            )
            .withKeySchema(
                new KeySchemaElement(KeysDynamoDb.KEY_ACCOUNT_UUID, KeyType.HASH),
                new KeySchemaElement(KeysDynamoDb.KEY_DEVICE_ID_KEY_ID, KeyType.RANGE)
            )
    );

    // Accounts
    createTableAndWait(configuration, namespace, WhisperServerConfiguration::getAccountsDynamoDbConfiguration,
        new CreateTableRequest()
            .withTableName(configuration.getAccountsDynamoDbConfiguration().getTableName())
            .withBillingMode(BillingMode.PAY_PER_REQUEST)
            .withAttributeDefinitions(
                new AttributeDefinition(AccountsDynamoDb.KEY_ACCOUNT_UUID, ScalarAttributeType.B)
            )
            .withKeySchema(
                new KeySchemaElement(AccountsDynamoDb.KEY_ACCOUNT_UUID, KeyType.HASH)
            )
    );

    // MigrationDeletedAccounts
    createTableAndWait(configuration, namespace,
        WhisperServerConfiguration::getMigrationDeletedAccountsDynamoDbConfiguration,
        new CreateTableRequest()
            .withTableName(configuration.getMigrationDeletedAccountsDynamoDbConfiguration().getTableName())
            .withBillingMode(BillingMode.PAY_PER_REQUEST)
            .withAttributeDefinitions(
                new AttributeDefinition(MigrationDeletedAccounts.KEY_UUID, ScalarAttributeType.B)
            )
            .withKeySchema(
                new KeySchemaElement(MigrationDeletedAccounts.KEY_UUID, KeyType.HASH)
            )
    );

    // MigrationRetryAccounts
    createTableAndWait(configuration, namespace,
        WhisperServerConfiguration::getMigrationRetryAccountsDynamoDbConfiguration,
        new CreateTableRequest()
            .withTableName(configuration.getMigrationRetryAccountsDynamoDbConfiguration().getTableName())
            .withBillingMode(BillingMode.PAY_PER_REQUEST)
            .withAttributeDefinitions(
                new AttributeDefinition(MigrationRetryAccounts.KEY_UUID, ScalarAttributeType.B)
            )
            .withKeySchema(
                new KeySchemaElement(MigrationRetryAccounts.KEY_UUID, KeyType.HASH)
            )
    );
  }

  private void createTableAndWait(WhisperServerConfiguration configuration, Namespace namespace,
      Function<WhisperServerConfiguration, DiskuvDynamoDBConfiguration> dynamoDBConfigurationFunction,
      final com.amazonaws.services.dynamodbv2.model.CreateTableRequest createTableRequest)
      throws InterruptedException, IOException {
    String tableName = createTableRequest.getTableName();

    final DiskuvDynamoDBConfiguration ddbConfig = dynamoDBConfigurationFunction.apply(configuration);
    customizeConfiguration(namespace, ddbConfig);

    AmazonDynamoDB client = new AwsClientFactory(ddbConfig).getAmazonDynamoDBClientBuilder().build();

    // check if already exists
    try {
      client.describeTable(tableName);
      logger.info("{}. The table exists", tableName);
      return;
    } catch (com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException e) {
      // expected!
    }

    // create
    logger.info("{}. Will create the table because it does not yet exist", tableName);
    client.createTable(createTableRequest);

    // wait until ACTIVE
    for (int i = 0; i < 30; ++i) {
      DescribeTableResult waitResult = client.describeTable(tableName);
      String tableStatus = waitResult.getTable().getTableStatus();
      if ("ACTIVE".equals(tableStatus)) {
        logger.info("{}. The table is now active.", tableName);
        return;
      }
      if (!"CREATING".equals(tableStatus) || !"UPDATING".equals(tableStatus)) {
        throw new IOException("The table " + tableName + " was in status " + tableStatus);
      }
      logger.info("{}. Waiting for table to be active ...", tableName);
      TimeUnit.SECONDS.sleep(5);
    }
    throw new IOException("The table " + tableName + " could not be created");

  }

  private void customizeConfiguration(Namespace namespace, DiskuvDynamoDBConfiguration ddbConfig) {
    // arguments
    boolean awsDefault = Boolean.TRUE.equals(namespace.getBoolean("aws-default"));

    // setup config
    if (awsDefault) {
      ddbConfig.setCredentialsType(DiskuvAwsCredentialsType.DEFAULT);
    }
  }

}
