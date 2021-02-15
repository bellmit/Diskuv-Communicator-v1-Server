package com.diskuv.communicator.configurator;

import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import org.bouncycastle.util.io.pem.PemReader;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;
import org.whispersystems.textsecuregcm.util.Base64;
import picocli.CommandLine;

import java.io.File;
import java.io.FileReader;
import java.util.concurrent.Callable;

import static com.diskuv.communicator.configurator.ConfigurationUtils.createConfigurationBuilder;

/** Modifies an existing YAML file. */
@CommandLine.Command(
    name = "view-client",
    mixinStandardHelpOptions = true,
    description =
        "View the client configuration that corresponds to the WhisperServer configuration. "
            + "The config must be present in both Signal-iOS/SignalServiceKit/src/TSConstants.swift and "
            + "Signal-Android/app/build.gradle, with the correct Base64 encoding given by this command"
)
public class ViewClientConfiguration implements Callable<Integer> {
  @CommandLine.Parameters(
      index = "0",
      paramLabel = "YAML_CONFIG_FILE",
      description = "The YAML configuration file for the server")
  protected File yamlServerFile;

  @CommandLine.Parameters(
          index = "1",
          paramLabel = "SERVER_CERTIFICATE_SIGNING_KEYPAIR_FILE",
          description = "PEM-encoded server certificate signing key pair. Only the public key is needed")
  protected File serverCertificateSigningKeyPairFile;

  public static void main(String... args) {
    int exitCode =
        new CommandLine(new ViewClientConfiguration())
            .setExecutionExceptionHandler(new PrintExceptionMessageHandler())
            .execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // load configuration
    WhisperServerConfiguration config = createConfigurationBuilder().build(yamlServerFile);

    // load public key from key pair
    ECPublicKey signingPublicKey;
    try (FileReader fileReader = new FileReader(serverCertificateSigningKeyPairFile);
        PemReader reader = new PemReader(fileReader)) {
      PemUtils.PublicPrivateKeyPair signingKeyPair = PemUtils.getKeyPair(reader);
      signingPublicKey = signingKeyPair.getPublicKey();
    }

    // view client configuration
    System.out.println();
    System.out.println();
    System.out.println("Zero-configuration public parameters");
    System.out.println("------------------------------------");
    System.out.println();
    System.out.println("ZKGROUP_SERVER_PUBLIC_PARAMS (Android), serverPublicParamsBase64 (iOS) := ");
    System.out.println("  "  + Base64.encodeBytes(config.getZkConfig().getServerPublic()));
    System.out.println("Examples:");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/fc41fb50144791deb17a3b240ebc4b84cbaf4ad3/app/build.gradle#L129");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/fc41fb50144791deb17a3b240ebc4b84cbaf4ad3/app/build.gradle#L259");
    System.out.println("* https://github.com/signalapp/Signal-iOS/blob/bac01d739403afc9bfc3d4997adc5e4a28612044/SignalServiceKit/src/TSConstants.swift#L197");
    System.out.println("* https://github.com/signalapp/Signal-iOS/blob/bac01d739403afc9bfc3d4997adc5e4a28612044/SignalServiceKit/src/TSConstants.swift#L251");
    System.out.println();

    System.out.println("Unidentified sender trust root");
    System.out.println("------------------------------");
    System.out.println();
    System.out.println("UNIDENTIFIED_SENDER_TRUST_ROOT (Android), kUDTrustRoot (iOS) := ");
    System.out.println("  "  + Base64.encodeBytes(signingPublicKey.serialize()));
    System.out.println("Examples:");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/fc41fb50144791deb17a3b240ebc4b84cbaf4ad3/app/build.gradle#L128");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/fc41fb50144791deb17a3b240ebc4b84cbaf4ad3/app/build.gradle#L258");
    System.out.println("* https://github.com/signalapp/Signal-iOS/blob/bac01d739403afc9bfc3d4997adc5e4a28612044/SignalServiceKit/src/TSConstants.swift#L165");
    System.out.println("* https://github.com/signalapp/Signal-iOS/blob/bac01d739403afc9bfc3d4997adc5e4a28612044/SignalServiceKit/src/TSConstants.swift#L212");

    return 0;
  }
}