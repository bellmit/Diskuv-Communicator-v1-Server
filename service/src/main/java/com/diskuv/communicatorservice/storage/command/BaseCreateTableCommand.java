package com.diskuv.communicatorservice.storage.command;

import com.diskuv.communicatorservice.storage.configuration.DiskuvGroupsConfiguration;
import io.dropwizard.cli.ConfiguredCommand;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;

public abstract class BaseCreateTableCommand extends ConfiguredCommand<WhisperServerConfiguration> {
  protected BaseCreateTableCommand(String name, String description) {
    super(name, description);
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser
        .addArgument(new String[] {"--aws-default"})
        .action(Arguments.storeTrue())
        .dest("aws-default")
        .setDefault(Boolean.FALSE)
        .help(
            "Use AWS default credentials, which among other things can use AWS_ environment variables or your default profile. If not specified, uses what is in the configuration");
  }

  protected DiskuvGroupsConfiguration customizeConfiguration(
          Namespace namespace, WhisperServerConfiguration configuration) {
    // arguments
    boolean awsDefault = Boolean.TRUE.equals(namespace.getBoolean("aws-default"));

    // setup config
    DiskuvGroupsConfiguration config = configuration.getDiskuvGroupsConfiguration();
    if (awsDefault) {
      config.setCredentialsType(DiskuvGroupsConfiguration.CredentialsType.DEFAULT);
    }
    return config;
  }

  protected <T> void createTable(DynamoDbAsyncTable<T> table) {
    table.createTable().join();
  }
}
