package com.diskuv.communicator.configurator;

import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import org.apache.commons.codec.binary.Hex;
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
import org.whispersystems.textsecuregcm.entities.MessageProtos;
import picocli.CommandLine;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static com.diskuv.communicator.configurator.ConfigurationUtils.mapperForWriting;
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
    private static final int UNIDENTIFIED_DELIVERY_KEY_ID_0 = 0;
    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    protected ApplicationConnection applicationConnection;

    static class ApplicationHttpsConnection {
        @CommandLine.Option(names = {"--application-https-port"}, description = "The HTTPS port to use for the application",
                required = true)
        protected int httpsPort;
        @CommandLine.Option(names = {"--application-keystore-file"}, description = "The keystore of the SSL certificate",
                required = true)
        protected File keystoreFile;
        @CommandLine.Option(names = {"--application-keystore-password"}, description = "The password to the SSL keystore certificate",
                required = true)
        protected String keystorePassword;
    }

    static class ApplicationConnection {
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

    private Random random;

    public static void main(String... args) {
        int exitCode = new CommandLine(new GenerateConfiguration()).setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        this.random = new SecureRandom();

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
        }
    }

    /**
     * Zero knowledge configuration. See https://eprint.iacr.org/2019/1416.pdf
     * Generating a new one?
     * 1. java -jar service/target/TextSecureServer-*.jar zkparams
     *
     * <pre>
     * zkConfig:
     *   # 160 bytes (org.signal.zkgroup.ServerPublicParams.SIZE).
     *   # base64 encoded.
     *   serverPublic: zG7l0tDo26hPEIE...tJS1iu9hRA
     *   # 896 bytes (org.signal.zkgroup.ServerSecretParams.SIZE).
     *   # base64 encoded.
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

    public void unidentifiedDelivery(WhisperServerConfiguration config) throws IllegalAccessException, InvalidKeyException {
        UnidentifiedDeliveryConfiguration value = new UnidentifiedDeliveryConfiguration();

        // generate certificate authority
        ECKeyPair caKeyPair = Curve.generateKeyPair();
        ECPrivateKey caKey = caKeyPair.getPrivateKey();

        // generate certificate and key with id=0
        ECKeyPair keyPair = Curve.generateKeyPair();
        byte[] certificate = MessageProtos.ServerCertificate.Certificate.newBuilder()
                .setId(UNIDENTIFIED_DELIVERY_KEY_ID_0)
                .setKey(ByteString.copyFrom(keyPair.getPublicKey().serialize()))
                .build()
                .toByteArray();
        byte[] signature = Curve.calculateSignature(caKey, certificate);
        byte[] signedCertificate = MessageProtos.ServerCertificate.newBuilder()
                .setCertificate(ByteString.copyFrom(certificate))
                .setSignature(ByteString.copyFrom(signature))
                .build()
                .toByteArray();
        setField(value, "certificate", signedCertificate);
        setField(value, "privateKey", keyPair.getPrivateKey().serialize());

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

    private void setUserAuthenticationTokenSharedSecret(Object value) throws IllegalAccessException {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        setField(value, "userAuthenticationTokenSharedSecret", Hex.encodeHexString(secret));
    }

    public void remoteConfig(WhisperServerConfiguration config) throws IllegalAccessException {
        RemoteConfigConfiguration value = new RemoteConfigConfiguration();

        setField(config, "remoteConfig", value);
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
