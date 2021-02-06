package com.diskuv.communicator.configurator;

import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jackson.Jackson;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.configuration.*;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;

import static com.diskuv.communicator.configurator.ConfigurationUtils.mapperForWriting;
import static com.diskuv.communicator.configurator.ConfigurationUtils.setField;

/** Modifies an existing YAML file. */
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
          "Output YAML file. If not specified, standard output is used. "
              + "Consider using a file in /dev/shm/")
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

    @CommandLine.Option(
        names = "--aws-access-secret-file",
        required = true,
        description = "Consider using a file in /dev/shm/")
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
          "URL (redis://...) of each Redis replica, "
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
    ObjectMapper mapperForReading = Jackson.newObjectMapper(new YAMLFactory());

    // load configuration
    WhisperServerConfiguration config =
        mapperForReading.readValue(inputYamlFile, WhisperServerConfiguration.class);

    // modify configuration
    awsAttachments(config);
    cdn(config);
    twilio(config);
    voiceVerification(config);
    messageStore(config);
    abuseDatabase(config);
    accountsDatabase(config);
    cache(config);
    pubsub(config);
    pushScheduler(config);
    messageCache(config);

    // write configuration
    ObjectMapper mapperForWriting = mapperForWriting();
    if (outputYamlFile != null) {
      mapperForWriting.writeValue(outputYamlFile, config);
    } else {
      String configValue = mapperForWriting.writeValueAsString(config);
      System.out.println(configValue);
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

  public void voiceVerification(WhisperServerConfiguration config) throws IllegalAccessException {
    VoiceVerificationConfiguration value = config.getVoiceVerificationConfiguration();
    if (voiceVerificationUrl != null) {
      setField(value, "url", voiceVerificationUrl);
    }
  }

  public void messageStore(WhisperServerConfiguration config) throws IllegalAccessException {
    DatabaseConfiguration value = config.getMessageStoreConfiguration();
    setDatabaseUrl(value, "signal_message");
  }

  public void abuseDatabase(WhisperServerConfiguration config) throws IllegalAccessException {
    DatabaseConfiguration value = config.getAbuseDatabaseConfiguration();
    setDatabaseUrl(value, "signal_abuse");
  }

  public void accountsDatabase(WhisperServerConfiguration config) throws IllegalAccessException {
    DatabaseConfiguration value = config.getAccountsDatabaseConfiguration();
    setDatabaseUrl(value, "signal_account");
  }

  public void cache(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisConfiguration value = config.getCacheConfiguration();
    setRedisUrls(value);
  }

  public void pubsub(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisConfiguration value = config.getPubsubCacheConfiguration();
    setRedisUrls(value);
  }

  public void pushScheduler(WhisperServerConfiguration config) throws IllegalAccessException {
    RedisConfiguration value = config.getPushScheduler();
    setRedisUrls(value);
  }

  public void messageCache(WhisperServerConfiguration config) throws IllegalAccessException {
    MessageCacheConfiguration value = config.getMessageCacheConfiguration();
    RedisConfiguration redis = value.getRedisConfiguration();
    setRedisUrls(redis);
  }

  private void setRedisUrls(RedisConfiguration value) throws IllegalAccessException {
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
