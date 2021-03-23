package com.diskuv.communicatorservice.storage.command;

import com.diskuv.communicatorservice.storage.HousesDao;
import com.diskuv.communicatorservice.storage.clients.AwsClientFactory;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

public class CreateTableHousesCommand extends BaseCreateTableCommand {
  public CreateTableHousesCommand() {
    super("createhousestable", "Create the Houses DynamoDB table");
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
    HousesDao dao = new HousesDao(dbAsyncClient, config.getHouseTableName());

    // create table
    createTable(dao.getTable());
  }
}
