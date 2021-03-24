package com.diskuv.communicatorservice.storage.command;

import com.diskuv.communicatorservice.storage.SanctuariesDao;
import com.diskuv.communicatorservice.storage.clients.AwsClientFactory;
import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

public class CreateTableSanctuariesCommand extends BaseCreateTableCommand {
  public CreateTableSanctuariesCommand() {
    super("createsanctuariestable", "Create the Sanctuaries DynamoDB table");
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
    SanctuariesDao dao = new SanctuariesDao(dbAsyncClient, config.getSanctuaryTableName());

    // create table
    createTable(dao.getTable());
  }
}
