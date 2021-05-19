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
package com.diskuv.communicator.configurator.dropwizard;

import static com.diskuv.communicator.configurator.dropwizard.ConfigurationUtils.convertToYaml;
import static com.diskuv.communicator.configurator.dropwizard.ConfigurationUtils.createConfigurationBuilder;
import static com.diskuv.communicator.configurator.dropwizard.ConfigurationUtils.setField;

import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import io.lettuce.core.RedisURI;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.configuration.AwsAttachmentsConfiguration;
import org.whispersystems.textsecuregcm.configuration.CdnConfiguration;
import org.whispersystems.textsecuregcm.configuration.DatabaseConfiguration;
import org.whispersystems.textsecuregcm.configuration.DynamoDbConfiguration;
import org.whispersystems.textsecuregcm.configuration.MessageCacheConfiguration;
import org.whispersystems.textsecuregcm.configuration.RedisClusterConfiguration;
import org.whispersystems.textsecuregcm.configuration.RedisConfiguration;
import org.whispersystems.textsecuregcm.configuration.TwilioConfiguration;
import org.whispersystems.textsecuregcm.configuration.VoiceVerificationConfiguration;
import picocli.CommandLine;

/**
 * Modifies an existing YAML file.
 */
@CommandLine.Command(
    name = "modify",
    mixinStandardHelpOptions = true,
    description =
        "Change the dynamic parts of the WhisperServer YAML configuration file. "
            + "Meant to be run during the startup of the server, and should modify an initial "
            + "file generated by the 'generate' command which has been hand-edited")
public class ModifyConfiguration implements Callable<Integer> {

  @CommandLine.Parameters(
      index = "0",
      description = "The input YAML file which will not be modified")
  protected File inputYamlFile;

  @CommandLine.Option(
      names = {"-o", "--output-file"},
      description =
          "Output YAML file. If not specified, standard output is used")
  protected File outputYamlFile;

  @CommandLine.Option(
      names = {"-r", "--aws-region"},
      description = "AWS region")
  protected String region;

  @CommandLine.Option(
      names = {"--aws-access-key"},
      description = "AWS access key for S3 buckets")
  protected String awsAccessKey;

  static class AccessSecret {

    @CommandLine.Option(names = "--aws-access-secret", required = true)
    String secret;

    @CommandLine.Option(names = "--aws-access-secret-file", required = true)
    File secretFile;
  }

  @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
  protected AccessSecret awsAccessSecret;

  @CommandLine.Option(
      names = {"--domain-name"},
      description =
          "Domain name of load balancer or API gateway fronting "
              + "this server without any https:// prefix")
  protected String domainName;

  @CommandLine.Option(
      names = {"--voice-verification-url"},
      description = "URL of webpath that contains " + "'verification.wav' and other voice assets")
  protected String voiceVerificationUrl;

  @CommandLine.Option(
      names = {"--database-host"},
      description =
          "Hostname of PostgreSQL database. May include a colon separated port (ex. localhost:5432)")
  protected String databaseHostAndPerhapsPort;

  @CommandLine.Option(
      names = {"--redis-cluster-url"},
      split = ",",
      description =
          "URL (redis://...) of each Redis cluster master node, "
              + "specified with comma-separated values or repeating the --redis-cluster-url option. "
              + "Format: https://lettuce.io/core/release/api/io/lettuce/core/RedisURI.html")
  protected String[] redisClusterUrls;

  @CommandLine.Option(
      names = {"--redis-cluster-database-number-pass-through"},
      description = "When specified, the database number of the first _pre-modified_ Redis Cluster URL will be used in " +
          "the _modified_ Redis Cluster URL")
  protected boolean redisClusterDatabaseNumberPassThrough;

  @CommandLine.Option(
      names = {"--redis-primary-host"},
      description =
          "Hostname of Redis cluster or primary Redis host. "
              + "Ignored if --redis-primary-url is specified")
  protected String redisPrimaryHost;

  @CommandLine.Option(
      names = {"--redis-primary-url"},
      description = "URL (redis://...) of Redis cluster or primary Redis host")
  protected String redisPrimaryUrl;

  @CommandLine.Option(
      names = {"--redis-replica-host"},
      split = ",",
      description =
          "Hostname of each Redis replica, "
              + "specified with comma-separated values or repeating the --redis-replica-host option. "
              + "Ignored if --redis-replica-url is specified")
  protected String[] redisReplicaHosts;

  @CommandLine.Option(
      names = {"--redis-replica-url"},
      split = ",",
      description =
          "URL (redis://...) of each Redis readonly replica, "
              + "specified with comma-separated values or repeating the --redis-replica-url option")
  protected String[] redisReplicaUrls;

  public static void main(String... args) {
    int exitCode =
        new CommandLine(new ModifyConfiguration())
            .setExecutionExceptionHandler(new PrintExceptionMessageHandler())
            .execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // load configuration
    WhisperServerConfiguration config = createConfigurationBuilder().build(inputYamlFile);

    // modify configuration
    awsAttachments(config);
    cdn(config);
    twilio(config);
    cacheCluster(config);
    pubsub(config);
    metricsCluster(config);
    rateLimitersCluster(config);
    pushSchedulerCluster(config);
    messageCache(config);
    clientPresenceCluster(config);
    abuseDatabase(config);
    accountsDatabase(config);
    voiceVerification(config);

    // write configuration
    String configYaml = convertToYaml(config);
    if (outputYamlFile != null) {
      Files.writeString(outputYamlFile.toPath(), configYaml);
    } else {
      System.out.println(configYaml);
    }

    return 0;
  }

  public void awsAttachments(WhisperServerConfiguration config)
      throws IllegalAccessException, IOException {
    AwsAttachmentsConfiguration value = config.getAwsAttachmentsConfiguration();
    if (region != null) {
      setField(value, "region", region);
    }
    setAwsAccess(value);
  }

  public void cdn(WhisperServerConfiguration config) throws IllegalAccessException, IOException {
    CdnConfiguration value = config.getCdnConfiguration();
    if (region != null) {
      setField(value, "region", region);
    }
    setAwsAccess(value);
  }

  public void twilio(WhisperServerConfiguration config) throws IllegalAccessException {
    TwilioConfiguration value = config.getTwilioConfiguration();
    if (domainName != null) {
      setField(value, "localDomain", domainName);
    }
  }

  public void cacheCluster(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisClusterConfiguration value = config.getCacheClusterConfiguration();
    setRedisClusterUrls(value);
  }

  public void pubsub(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisConfiguration value = config.getPubsubCacheConfiguration();
    setRedisUrlAndReplicas(value);
  }

  public void metricsCluster(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisClusterConfiguration value = config.getMetricsClusterConfiguration();
    setRedisClusterUrls(value);
  }

  public void pushSchedulerCluster(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisClusterConfiguration value = config.getPushSchedulerCluster();
    setRedisClusterUrls(value);
  }

  public void rateLimitersCluster(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisClusterConfiguration value = config.getRateLimitersCluster();
    setRedisClusterUrls(value);
  }

  public void messageCache(WhisperServerConfiguration config) throws IllegalAccessException {
    MessageCacheConfiguration value = config.getMessageCacheConfiguration();
    RedisClusterConfiguration redis = value.getRedisClusterConfiguration();
    setRedisClusterUrls(redis);
  }

  public void clientPresenceCluster(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisClusterConfiguration value = config.getClientPresenceClusterConfiguration();
    setRedisClusterUrls(value);
  }

  public void abuseDatabase(WhisperServerConfiguration config) throws IllegalAccessException {
    DatabaseConfiguration value = config.getAbuseDatabaseConfiguration();
    setDatabaseUrl(value, "signal_abuse");
  }

  public void accountsDatabase(WhisperServerConfiguration config) throws IllegalAccessException {
    DatabaseConfiguration value = config.getAccountsDatabaseConfiguration();
    setDatabaseUrl(value, "signal_account");
  }

  public void voiceVerification(WhisperServerConfiguration config) throws IllegalAccessException {
    VoiceVerificationConfiguration value = config.getVoiceVerificationConfiguration();
    if (voiceVerificationUrl != null) {
      setField(value, "url", voiceVerificationUrl);
    }
  }

  private void setRedisClusterUrls(RedisClusterConfiguration value) throws IllegalAccessException {
    // All of the existing urls will be replaced, but if there is at least one we will include the
    // special query parameters of the first existing url. This lets fields like 'database' (the Redis database
    // number) be set.
    List<String> existingUrls = value.getUrls();
    Integer database_ = null;
    if (redisClusterDatabaseNumberPassThrough && existingUrls != null && !existingUrls.isEmpty()) {
      RedisURI firstUri = RedisURI.create(existingUrls.get(0));
      database_ = firstUri.getDatabase();
    }
    final Integer database = database_;

    List<String> urls = new ArrayList<>();
    if (redisClusterUrls != null && redisClusterUrls.length > 0) {
      urls.addAll(ImmutableList.copyOf(redisClusterUrls).stream()
          .map(RedisURI::create)
          .map(uri -> {
            if (database != null) {
              uri.setDatabase(database);
            }
            return uri;
          })
          .map(uri -> uri.toURI().toString())
          .collect(Collectors.toList()));
    }
    setField(value, "urls", ImmutableList.copyOf(urls));
  }

  private void setRedisUrlAndReplicas(RedisConfiguration value) throws IllegalAccessException {
    if (redisPrimaryUrl != null) {
      setField(value, "url", redisPrimaryUrl);
    } else if (redisPrimaryHost != null) {
      setField(value, "url", "redis://" + redisPrimaryHost + ":6379");
    }
    if (redisReplicaUrls != null && redisReplicaUrls.length > 0) {
      setField(value, "replicaUrls", ImmutableList.copyOf(redisReplicaUrls));
    } else if (redisReplicaHosts != null && redisReplicaHosts.length > 0) {
      setField(
          value,
          "replicaUrls",
          Arrays.stream(redisReplicaHosts)
              .map(hostname -> "redis://" + hostname + ":6379")
              .collect(ImmutableList.toImmutableList()));
    }
  }

  private void setDatabaseUrl(DatabaseConfiguration value, String database)
      throws IllegalAccessException {
    if (databaseHostAndPerhapsPort != null) {
      setField(value, "url", "jdbc:postgresql://" + databaseHostAndPerhapsPort + "/" + database);
    }
  }

  private void setDynamoDbConfiguration(DynamoDbConfiguration dbConfiguration, String tableName)
      throws IllegalAccessException {
    if (region != null) {
      setField(dbConfiguration, "region", region);
    }
    setField(dbConfiguration, "tableName", tableName);
  }

  private void setAwsAccess(Object value) throws IllegalAccessException, IOException {
    if (awsAccessKey != null) {
      setField(value, "accessKey", awsAccessKey);
    }
    if (awsAccessSecret != null) {
      final String secret;
      if (awsAccessSecret.secret != null) {
        secret = awsAccessSecret.secret;
      } else if (awsAccessSecret.secretFile != null) {
        secret = Files.readString(awsAccessSecret.secretFile.toPath(), StandardCharsets.UTF_8);
      } else {
        secret = null;
      }
      if (secret != null) {
        setField(value, "accessSecret", secret);
      }
    }
  }
}
