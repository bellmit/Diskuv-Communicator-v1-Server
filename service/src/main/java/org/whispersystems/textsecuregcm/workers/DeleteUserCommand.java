package org.whispersystems.textsecuregcm.workers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.providers.RedisClientFactory;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Accounts;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.FaultTolerantDatabase;
import org.whispersystems.textsecuregcm.util.Base64;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.stream.Collectors;

import io.dropwizard.Application;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.setup.Environment;

public class DeleteUserCommand extends EnvironmentCommand<WhisperServerConfiguration> {

  private final Logger logger = LoggerFactory.getLogger(DeleteUserCommand.class);

  public DeleteUserCommand() {
    super(new Application<WhisperServerConfiguration>() {
      @Override
      public void run(WhisperServerConfiguration configuration, Environment environment)
          throws Exception
      {

      }
    }, "rmuser", "remove user");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("-u", "--user")
             .dest("user")
             .type(String.class)
             .required(true)
             .help("The user to remove");
  }

  @Override
  protected void run(Environment environment, Namespace namespace,
                     WhisperServerConfiguration configuration)
      throws Exception
  {
    try {
      String[] users = namespace.getString("user").split(",");

      environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

      JdbiFactory           jdbiFactory     = new JdbiFactory();
      Jdbi                  accountJdbi     = jdbiFactory.build(environment, configuration.getAccountsDatabaseConfiguration(), "accountdb");
      FaultTolerantDatabase accountDatabase = new FaultTolerantDatabase("account_database_delete_user", accountJdbi, configuration.getAccountsDatabaseConfiguration().getCircuitBreakerConfiguration());

      RedisClusterClient cacheClusterClient  = RedisClusterClient.create(configuration.getCacheClusterConfiguration().getUrls().stream().map(RedisURI::create).collect(Collectors.toList()));
      FaultTolerantRedisCluster cacheCluster = new FaultTolerantRedisCluster("main_cache_cluster", cacheClusterClient, configuration.getCacheClusterConfiguration().getCircuitBreakerConfiguration());

      Accounts            accounts        = new Accounts(accountDatabase);
      ReplicatedJedisPool cacheClient     = new RedisClientFactory("main_cache_delete_command", configuration.getCacheConfiguration().getReadTimeoutMs(), configuration.getCacheConfiguration().getUrl(), configuration.getCacheConfiguration().getReplicaUrls(), configuration.getCacheConfiguration().getCircuitBreakerConfiguration()).getRedisClientPool();
      AccountsManager     accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster);

      for (String user: users) {
        Optional<Account> account = accountsManager.get(user);

        if (account.isPresent()) {
          Optional<Device> device = account.get().getDevice(1);

          if (device.isPresent()) {
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);

            device.get().setGcmId(null);
            device.get().setFetchesMessages(false);
            device.get().setAuthenticationCredentials(new AuthenticationCredentials(Base64.encodeBytes(random)));

            accountsManager.update(account.get());

            logger.warn("Removed " + account.get().getNumber());
          } else {
            logger.warn("No primary device found...");
          }
        } else {
          logger.warn("Account not found...");
        }
      }
    } catch (Exception ex) {
      logger.warn("Removal Exception", ex);
      throw new RuntimeException(ex);
    }
  }
}
