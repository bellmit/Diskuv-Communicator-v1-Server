package org.whispersystems.textsecuregcm.configuration.dynamic;

public class DiskuvDynamicConfigurations {

  public static DynamicConfiguration getDefault() {
    org.whispersystems.textsecuregcm.configuration.dynamic.DynamicAccountsDynamoDbMigrationConfiguration migrationConfiguration = new org.whispersystems.textsecuregcm.configuration.dynamic.DynamicAccountsDynamoDbMigrationConfiguration();
    migrationConfiguration.setDeleteEnabled(true);
    migrationConfiguration.setReadEnabled(true);
    migrationConfiguration.setWriteEnabled(true);

    DynamicConfiguration dynamicConfiguration = new DynamicConfiguration();
    dynamicConfiguration.setAccountsDynamoDbMigration(migrationConfiguration);
    dynamicConfiguration.setExperiments(com.google.common.collect.ImmutableMap.of(
        "accountsDynamoDbMigration", new org.whispersystems.textsecuregcm.configuration.dynamic.DynamicExperimentEnrollmentConfiguration().setEnrollmentPercentage(100)
    ));

    return dynamicConfiguration;
  }
}
