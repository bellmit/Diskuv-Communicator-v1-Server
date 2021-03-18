package com.diskuv.communicator.configurator;

import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import io.dropwizard.http2.Http2ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;
import org.passay.CharacterData;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.PasswordGenerator;
import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.ServerSecretParams;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.configuration.*;
import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECKeyPair;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;
import org.whispersystems.textsecuregcm.entities.MessageProtos.ServerCertificate;
import org.whispersystems.textsecuregcm.metrics.CollectdMetricsReporterFactory;
import org.whispersystems.textsecuregcm.util.Util;
import picocli.CommandLine;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static com.diskuv.communicator.configurator.ConfigurationUtils.convertToYaml;
import static com.diskuv.communicator.configurator.ConfigurationUtils.setField;

/**
 * Prints a YAML file that has all of the fields necessary for the server. If there
 * are defaults, those defaults will be in the YAML file.
 */
@CommandLine.Command(name = "generate", mixinStandardHelpOptions = true,
        description = "Create a WhisperServer YAML configuration file " +
                "using auto-generated secrets and TODO placeholders. " +
                "You are meant to run this _once_ per WhisperServer cluster, and then have its TODOs replaced with " +
                "real configuration values, and finally you place it in secure storage (ex. " +
                "AWS Parameter Store SecureString). See the help for the 'modify' command, which allows customization " +
                "of configuration that may change every reboot")
public class GenerateConfiguration implements Callable<Integer> {
    private static final int DATABASE_PASSWORD_LENGTH = 30;

    @CommandLine.Parameters(
        paramLabel = "SERVER_CERTIFICATE_SIGNING_KEYPAIR_FILE",
        description =
              "Will be re-used if it already exists, unless you explicitly use the option to overwrite the file. "
              + "You MUST keep this forever-living server certificate signing key pair in a secure location. "
              + "The server certificate is used to encrypt unidentified sender messages, and the signing key pair are used "
              + "to generate new server certificates in case of server compromise. "
              + "The file will be PEM encoded with both the signing private and public key")
    protected File serverCertificateSigningKeyPairFile;

    @CommandLine.Option(
      names = {"--overwrite-server-certificate-signing-keypair"},
      description =
          "If specified, "
              + "the SERVER_CERTIFICATE_SIGNING_KEYPAIR_FILE will be overwritten if it exists. Use with caution!")
    protected boolean overwriteServerCertificateSigningKeyPairFile;

    @CommandLine.Option(
      names = {"--unidentified-delivery-key-id"},
      description =
          "Each unidentified delivery key pair has an identifier. You should consider it a version number that you "
              + "increment each time you need a new key pair. The default is 1",
      defaultValue = "1")
    protected int unidentifiedDeliveryKeyId;

    @CommandLine.ArgGroup(multiplicity = "0..1")
    protected ApplicationConnection applicationConnection;

    static class ApplicationHttpsConnection {
        @CommandLine.Option(names = {"--application-https-port"}, description = "The HTTPS port to use for the application",
                required = true)
        protected int httpsPort;
        @CommandLine.Option(names = {"--application-https-keystore-file"}, description = "The keystore of the SSL certificate",
                required = true)
        protected File keystoreFile;
        @CommandLine.Option(names = {"--application-https-keystore-password"}, description = "The password to the SSL keystore certificate",
                required = true)
        protected String keystorePassword;
    }

    static class ApplicationH2Connection {
        @CommandLine.Option(names = {"--application-h2-port"}, description = "The HTTP/2 over TLS port to use for the application",
                required = true)
        protected int h2Port;
        @CommandLine.Option(names = {"--application-h2-keystore-file"}, description = "The keystore of the SSL certificate",
                required = true)
        protected File keystoreFile;
        @CommandLine.Option(names = {"--application-h2-keystore-password"}, description = "The password to the SSL keystore certificate",
                required = true)
        protected String keystorePassword;
    }

    static class ApplicationConnection {
        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..1")
        protected ApplicationH2Connection h2Connection;
        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..1")
        protected ApplicationHttpsConnection httpsConnection;
        @CommandLine.Option(names = {"--application-http-port"}, description = "The HTTP port to use for the application")
        protected Integer httpPort;
    }
    @CommandLine.Option(names = {"--admin-http-port"},
            description = "The HTTP port to use for administration. If not specified, no administration port is used")
    protected Integer adminHttpPort;

    @CommandLine.Option(names = {"-o", "--output-file"}, description = "Output YAML file. If not specified, " +
            "standard output is used")
    protected File outputYamlFile;

    static class MetricsCollectd {
        @CommandLine.Option(names = {"--metrics-collectd-host"}, description = "The collectd host to stream metric values",
                required = true)
        protected String host;
        @CommandLine.Option(names = {"--metrics-collectd-port"}, description = "The collectd port to stream metric values over UDP",
                required = true)
        protected int port;
    }
    @CommandLine.ArgGroup(exclusive = false, multiplicity = "0..1")
    protected MetricsCollectd metricsCollectd;

    private final Random random;

    public static void main(String... args) {
        int exitCode = new CommandLine(new GenerateConfiguration()).setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(args);
        System.exit(exitCode);
    }

    public GenerateConfiguration() {
        random = new SecureRandom();
    }

    @VisibleForTesting
    protected GenerateConfiguration(Random random) {
        this.random = random;
    }

    @Override
    public Integer call() throws Exception {
        WhisperServerConfiguration config = createWhisperServerConfiguration();

        // write configuration
        String configYaml = convertToYaml(config);
        if (outputYamlFile != null) {
            Files.writeString(outputYamlFile.toPath(), configYaml);
        } else {
            System.out.println(configYaml);
        }

        return 0;
    }

    @VisibleForTesting
    protected WhisperServerConfiguration createWhisperServerConfiguration() throws IllegalAccessException, InvalidKeyException, IOException {
        // create configuration
        WhisperServerConfiguration config = new WhisperServerConfiguration();

        // generate new secrets and set user-defined config
        server(config);
        twilio(config);
        zkConfig(config);
        push(config);
        awsAttachments(config);
        gcpAttachments(config);
        cdn(config);
        cache(config);
        pubsub(config);
        accountsDatabase(config);
        pushScheduler(config);
        messageCache(config);
        messageStore(config);
        abuseDatabase(config);
        accountsDatabase(config);
        turn(config);
        gcm(config);
        apn(config);
        unidentifiedDelivery(config);
        voiceVerification(config);
        recaptcha(config);
        storageService(config);
        backupService(config);
        remoteConfig(config);
        accountDatabaseCrawler(config);
        metrics(config);

        // Diskuv specific configurations
        diskuvSyntheticAccounts(config);
        jwtKeys(config);

        return config;
    }

    /**
     * <pre>
     * server:
     *   applicationConnectors:
     *     - type: http
     *       port: ((--application-http-port))
     *   adminConnectors:
     *     - type: http
     *       port: ((--admin-http-port))
     * </pre>
     */
    public void server(WhisperServerConfiguration config) {
        DefaultServerFactory serverFactory = (DefaultServerFactory) config.getServerFactory();
        if (adminHttpPort != null) {
            HttpConnectorFactory adminConnectorFactory = new HttpConnectorFactory();
            adminConnectorFactory.setPort(adminHttpPort);
            serverFactory.setAdminConnectors(ImmutableList.of(adminConnectorFactory));
        } else {
            serverFactory.setAdminConnectors(ImmutableList.of());
        }
        if (applicationConnection != null && applicationConnection.httpPort != null) {
            HttpConnectorFactory applicationConnectorFactory = new HttpConnectorFactory();
            applicationConnectorFactory.setPort(applicationConnection.httpPort);
            serverFactory.setApplicationConnectors(ImmutableList.of(applicationConnectorFactory));
        } else if (applicationConnection != null && applicationConnection.httpsConnection != null) {
            HttpsConnectorFactory applicationConnectorFactory = new HttpsConnectorFactory();
            applicationConnectorFactory.setPort(applicationConnection.httpsConnection.httpsPort);
            applicationConnectorFactory.setKeyStorePath(applicationConnection.httpsConnection.keystoreFile.getPath());
            applicationConnectorFactory.setKeyStorePassword(applicationConnection.httpsConnection.keystorePassword);
            serverFactory.setApplicationConnectors(ImmutableList.of(applicationConnectorFactory));
        } else if (applicationConnection != null && applicationConnection.h2Connection != null) {
            Http2ConnectorFactory applicationConnectorFactory = new Http2ConnectorFactory();
            applicationConnectorFactory.setPort(applicationConnection.h2Connection.h2Port);
            applicationConnectorFactory.setKeyStorePath(applicationConnection.h2Connection.keystoreFile.getPath());
            applicationConnectorFactory.setKeyStorePassword(applicationConnection.h2Connection.keystorePassword);
            serverFactory.setApplicationConnectors(ImmutableList.of(applicationConnectorFactory));
        }
    }

    /**
     * Zero knowledge configuration. See https://eprint.iacr.org/2019/1416.pdf
     * Generating a new one?
     * 1. java -jar service/target/TextSecureServer-*.jar zkparams
     *
     * <pre>
     * zkConfig:
     *   # 161 bytes (org.signal.zkgroup.ServerPublicParams.SIZE; zkgroup 0.7.0).
     *   # base64 encoded without padding.
     *   # note: the iOS/Android constant for this field is base64 encoded _with_ padding.
     *   serverPublic: zG7l0tDo26hPEIE...tJS1iu9hRA
     *   # 769 bytes (org.signal.zkgroup.ServerSecretParams.SIZE; zkgroup 0.7.0).
     *   # base64 encoded without padding.
     *   serverSecret: uggOoigNtWuPQ9p...bSUtYrvYUQ
     *   enabled: true
     * </pre>
     */
    public void zkConfig(WhisperServerConfiguration config) throws IllegalAccessException {
        ZkConfig value = new ZkConfig();
        setField(value, "enabled", true);

        // generate secret
        ServerSecretParams serverSecretParams = ServerSecretParams.generate();
        setField(value, "serverSecret", serverSecretParams.serialize());

        ServerPublicParams publicParams = serverSecretParams.getPublicParams();
        setField(value, "serverPublic", publicParams.serialize());
        setField(config, "zkConfig", value);
    }

    public void twilio(WhisperServerConfiguration config) throws IllegalAccessException {
        TwilioConfiguration value = new TwilioConfiguration();
        setField(value, "accountId", "TODO - see https://www.twilio.com/console");
        setField(value, "accountToken", "TODO - see https://www.twilio.com/console");
        setField(value, "numbers", ImmutableList.of("TODO - see https://www.twilio.com/console"));
        setField(value, "messagingServicesId", "TODO - see https://www.twilio.com/console/sms/services");
        setField(value, "localDomain", "TODO - the domain name Twilio can connect back to for calls. Do not prefix with https://");
        setField(config, "twilio", value);
    }

    public void push(WhisperServerConfiguration config) throws IllegalAccessException {
        PushConfiguration value = new PushConfiguration();
        setField(config, "push", value);
    }

    public void awsAttachments(WhisperServerConfiguration config) throws IllegalAccessException {
        AwsAttachmentsConfiguration value = new AwsAttachmentsConfiguration();
        setField(value, "accessKey", "TODO");
        setField(value, "accessSecret", "TODO");
        setField(value, "bucket", "TODO");
        setField(value, "region", "TODO");
        setField(config, "awsAttachments", value);
    }

    public void gcpAttachments(WhisperServerConfiguration config) throws IllegalAccessException {
        GcpAttachmentsConfiguration value = new GcpAttachmentsConfiguration();
        setField(value, "domain", "TODO - https://cloud.google.com/storage/docs/authentication/canonical-requests#about-resource-path");
        setField(value, "email", "TODO - https://console.cloud.google.com/iam-admin/serviceaccounts");
        setField(value, "rsaSigningKey", "-----BEGIN PRIVATE KEY-----\nTODO - https://cloud.google.com/iam/docs/service-accounts#service_account_keys\nTry: openssl pkcs12 -in service_account_key.p12 -out rsaSigningKey.pem -nocerts -nodes\n-----END PRIVATE KEY-----");
        setField(value, "pathPrefix", "");
        // 100MiB is hardcoded in org.whispersystems.textsecuregcm.controllers.AttachmentControllerV2.getAttachmentUploadForm
        setField(value, "maxSizeInBytes", 100 * 1024 * 1024);
        setField(config, "gcpAttachments", value);
    }

    public void cdn(WhisperServerConfiguration config) throws IllegalAccessException {
        CdnConfiguration value = new CdnConfiguration();
        setField(value, "accessKey", "TODO");
        setField(value, "accessSecret", "TODO");
        setField(value, "bucket", "TODO");
        setField(value, "region", "TODO");
        setField(config, "cdn", value);
    }

    public void cache(WhisperServerConfiguration config) throws IllegalAccessException {
        RedisConfiguration value = new RedisConfiguration();
        setRedisFields(value);
        setField(config, "cache", value);
    }

    public void pubsub(WhisperServerConfiguration config) throws IllegalAccessException {
        RedisConfiguration value = new RedisConfiguration();
        setRedisFields(value);
        setField(config, "pubsub", value);
    }

    public void accountDatabaseCrawler(WhisperServerConfiguration config) throws IllegalAccessException {
        AccountDatabaseCrawlerConfiguration value = new AccountDatabaseCrawlerConfiguration();

        setField(config, "accountDatabaseCrawler", value);
    }

    public void pushScheduler(WhisperServerConfiguration config) throws IllegalAccessException {
        RedisConfiguration value = new RedisConfiguration();
        setRedisFields(value);
        setField(config, "pushScheduler", value);
    }

    public void messageCache(WhisperServerConfiguration config) throws IllegalAccessException {
        MessageCacheConfiguration value = new MessageCacheConfiguration();
        RedisConfiguration redis = new RedisConfiguration();
        setRedisFields(redis);
        setField(value, "redis", redis);
        setField(config, "messageCache", value);
    }

    public void messageStore(WhisperServerConfiguration config) throws IllegalAccessException {
        DatabaseConfiguration value = new DatabaseConfiguration();
        setDatabaseFields(value, "signal_message");
        setField(config, "messageStore", value);
    }

    public void abuseDatabase(WhisperServerConfiguration config) throws IllegalAccessException {
        DatabaseConfiguration value = new DatabaseConfiguration();
        setDatabaseFields(value, "signal_abuse");
        setField(config, "abuseDatabase", value);
    }

    public void accountsDatabase(WhisperServerConfiguration config) throws IllegalAccessException {
        DatabaseConfiguration value = new DatabaseConfiguration();
        setDatabaseFields(value, "signal_account");
        setField(config, "accountsDatabase", value);
    }

    public void turn(WhisperServerConfiguration config) throws IllegalAccessException {
        TurnConfiguration value = new TurnConfiguration();

        // generate a secret used for HMAC-SHA1
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        setField(value, "secret", Hex.encodeHexString(secret));

        setField(value, "uris", ImmutableList.of("TODO - ex. stun:yourdomain:80",
                "TODO - ex. stun:yourdomain.com:443", "TODO - ex. turn:yourdomain:443?transport=udp",
                "TODO - ex. turn:etc.com:80?transport=udp"));

        setField(config, "turn", value);
    }

    public void gcm(WhisperServerConfiguration config) throws IllegalAccessException {
        GcmConfiguration value = new GcmConfiguration();
        setField(value, "apiKey", "TODO - https://console.firebase.google.com/ > Settings > Cloud Messaging. Include the 'senderId' field as well");
        setField(config, "gcm", value);
    }

    public void apn(WhisperServerConfiguration config) throws IllegalAccessException {
        ApnConfiguration value = new ApnConfiguration();

        setField(value, "bundleId", "TODO");
        setField(value, "pushCertificate", "-----BEGIN CERTIFICATE-----\nTODO: If you don't support iOS, use the following to create a mock p8 key:\nopenssl req -newkey rsa:2048 -new -nodes -x509 -days 3650 -keyout key.pem -out cert.pem -subj \"/C=US/ST=CA/L=SanDiego/O=Avengers/CN=www.tonystark.com\"\n-----END CERTIFICATE-----");
        setField(value, "pushKey", "-----BEGIN EC PRIVATE KEY-----\nTODO: If you don't support iOS, use the following to create a mock p8 key:\nopenssl ecparam -genkey -name prime256v1 -noout -out pushKey.pem\n-----END EC PRIVATE KEY-----");
        setField(config, "apn", value);
    }

    public void unidentifiedDelivery(WhisperServerConfiguration config) throws IllegalAccessException, InvalidKeyException, IOException {
        UnidentifiedDeliveryConfiguration value = new UnidentifiedDeliveryConfiguration();

        boolean shouldGenerate = overwriteServerCertificateSigningKeyPairFile || !serverCertificateSigningKeyPairFile.exists();
        final ECPrivateKey caKey;
        if (shouldGenerate) {
            // Generate certificate authority (aka signing key pair).
            // The private key below (`caKey`) is analogous to an Android signing key.
            ECKeyPair caKeyPair = Curve.generateKeyPair();
            caKey = caKeyPair.getPrivateKey();

            // Write the signing key pair in PEM format
            StringWriter sw = new StringWriter();
            PemWriter pemWriter = new PemWriter(sw);
            pemWriter.writeObject(new PemObject("PUBLIC KEY", caKeyPair.getPublicKey().serialize()));
            pemWriter.writeObject(new PemObject("PRIVATE KEY", caKey.serialize()));
            pemWriter.flush();
            Files.writeString(serverCertificateSigningKeyPairFile.toPath(), sw.toString());
        } else {
            try (FileReader fileReader = new FileReader(serverCertificateSigningKeyPairFile);
                 PemReader reader = new PemReader(fileReader)) {
                PemUtils.PublicPrivateKeyPair signingKeyPair = PemUtils.getKeyPair(reader);
                caKey = signingKeyPair.getPrivateKey();
            }
        }

        // Generate certificate and key with id=0.
        // The private key below (`privateKey`) is analogous to an Android upload key.
        // It looks like the intention from the Signal team is that you can change
        // to a new `keyPair` if the server is breached. It is probably best to
        // rotate the `keyPair` regularly.
        ECKeyPair keyPair = Curve.generateKeyPair();
        ECPrivateKey privateKey = keyPair.getPrivateKey();
        byte[] certificate = ServerCertificate.Certificate.newBuilder()
                .setId(unidentifiedDeliveryKeyId)
                .setKey(ByteString.copyFrom(keyPair.getPublicKey().serialize()))
                .build()
                .toByteArray();
        byte[] signature = Curve.calculateSignature(caKey, certificate);
        byte[] signedCertificate = ServerCertificate.newBuilder()
                .setCertificate(ByteString.copyFrom(certificate))
                .setSignature(ByteString.copyFrom(signature))
                .build()
                .toByteArray();
        setField(value, "certificate", signedCertificate);
        setField(value, "privateKey", privateKey.serialize());

        // https://github.com/signalapp/Signal-Android/blob/e0fc191883f257aaf11cb0da2b88252623d83f73/app/src/main/java/org/thoughtcrime/securesms/jobs/RotateCertificateJob.java#L30
        // is 1 day, so don't expire too much greater than that
        setField(value, "expiresDays", 3);

        setField(config, "unidentifiedDelivery", value);
    }

    public void voiceVerification(WhisperServerConfiguration config) throws IllegalAccessException {
        VoiceVerificationConfiguration value = new VoiceVerificationConfiguration();
        setField(value, "url", "TODO");
        setField(value, "locales", ImmutableList.of());
        setField(config, "voiceVerification", value);
    }

    public void recaptcha(WhisperServerConfiguration config) throws IllegalAccessException {
        RecaptchaConfiguration value = new RecaptchaConfiguration();
        setField(value, "secret", "TODO - http://www.google.com/recaptcha/admin");
        setField(config, "recaptcha", value);
    }

    public void storageService(WhisperServerConfiguration config) throws IllegalAccessException {
        SecureStorageServiceConfiguration value = new SecureStorageServiceConfiguration();

        // generate secret
        setUserAuthenticationTokenSharedSecret(value);

        setField(config, "storageService", value);
    }

    public void backupService(WhisperServerConfiguration config) throws IllegalAccessException {
        SecureBackupServiceConfiguration value = new SecureBackupServiceConfiguration();

        // generate secret
        setUserAuthenticationTokenSharedSecret(value);

        setField(config, "backupService", value);
    }

    public void metrics(WhisperServerConfiguration config) {
        if (this.metricsCollectd != null) {
            CollectdMetricsReporterFactory factory = new CollectdMetricsReporterFactory();
            factory.setHostname(this.metricsCollectd.host);
            factory.setPort(this.metricsCollectd.port);
            config.getMetricsFactory().setReporters(ImmutableList.of(factory));
        }
    }

    private void setUserAuthenticationTokenSharedSecret(Object value) throws IllegalAccessException {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        setField(value, "userAuthenticationTokenSharedSecret", Hex.encodeHexString(secret));
    }

    public void remoteConfig(WhisperServerConfiguration config) throws IllegalAccessException {
        RemoteConfigConfiguration value = new RemoteConfigConfiguration();

        setField(config, "remoteConfig", value);
    }

    private void jwtKeys(WhisperServerConfiguration config) throws IllegalAccessException {
        JwtKeysConfiguration value = new JwtKeysConfiguration();
        value.setDomain("TODO");

        setField(config, "jwtKeys", value);
    }

    private void diskuvSyntheticAccounts(WhisperServerConfiguration config) throws IllegalAccessException {
        DiskuvSyntheticAccountsConfiguration value = new DiskuvSyntheticAccountsConfiguration();
        value.setSharedEntropyInput(Util.generateSecretBytes(48));

        setField(config, "diskuvSyntheticAccounts", value);
    }

    private String generateDatabasePassword() {
        PasswordGenerator generator = new PasswordGenerator(random);
        List<CharacterRule> rules = Arrays.asList(
                // at least four upper-case characters
                new CharacterRule(EnglishCharacterData.UpperCase, 4),

                // at least four lower-case characters
                new CharacterRule(EnglishCharacterData.LowerCase, 4),

                // at least four digit characters
                new CharacterRule(EnglishCharacterData.Digit, 4),

                // at least four special characters, except no single quote (SQL escape character)
                new CharacterRule(new CharacterData() {
                    @Override
                    public String getErrorCode() {
                        return "ERR_SPECIAL";
                    }

                    @Override
                    public String getCharacters() {
                        return EnglishCharacterData.Special.getCharacters().replace("'", "");
                    }
                }, 4)

        );
        return "TODO - make your own or use: " + generator.generatePassword(DATABASE_PASSWORD_LENGTH, rules);
    }

    private void setRedisFields(RedisConfiguration value) throws IllegalAccessException {
        setField(value, "url", "redis://TODO:6379");
        setField(value, "replicaUrls", ImmutableList.of("redis://TODO:6379"));
    }

    private void setDatabaseFields(DatabaseConfiguration value, String database) throws IllegalAccessException {
        setField(value, "driverClass", "org.postgresql.Driver");
        setField(value, "url", "jdbc:postgresql://TODO:5432/" + database);
        setField(value, "user", database + "_user");
        setField(value, "password", generateDatabasePassword());
    }

}
