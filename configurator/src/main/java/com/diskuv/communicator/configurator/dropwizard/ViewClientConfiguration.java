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

import com.diskuv.communicator.configurator.PemUtils;
import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import org.bouncycastle.util.io.pem.PemReader;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;
import picocli.CommandLine;

import java.io.File;
import java.io.FileReader;
import java.util.concurrent.Callable;

import static com.diskuv.communicator.configurator.dropwizard.ConfigurationUtils.createConfigurationBuilder;

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
    System.out.println("  "  + java.util.Base64.getEncoder().encodeToString(config.getZkConfig().getServerPublic()));
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
    System.out.println("  "  + java.util.Base64.getEncoder().encodeToString(signingPublicKey.serialize()));
    System.out.println("Examples:");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/fc41fb50144791deb17a3b240ebc4b84cbaf4ad3/app/build.gradle#L128");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/fc41fb50144791deb17a3b240ebc4b84cbaf4ad3/app/build.gradle#L258");
    System.out.println("* https://github.com/signalapp/Signal-iOS/blob/bac01d739403afc9bfc3d4997adc5e4a28612044/SignalServiceKit/src/TSConstants.swift#L165");
    System.out.println("* https://github.com/signalapp/Signal-iOS/blob/bac01d739403afc9bfc3d4997adc5e4a28612044/SignalServiceKit/src/TSConstants.swift#L212");
    System.out.println();

    System.out.println("Google Firebase Messaging");
    System.out.println("-------------------------");
    System.out.println();
    System.out.println("gcm_defaultSenderId (Android) := ");
    System.out.println("  "  + config.getGcmConfiguration().getSenderId());
    System.out.println("firebase_database_url (Android, but not used) := ");
    System.out.println("  https://project-xxx.blackhole.firebaseio.com");
    System.out.println("remaining firebase_messaging.xml settings (Android) := ");
    System.out.println("  0. Make sure you have two \"projects\" in your Google Cloud account. One staging, one production ");
    System.out.println("  1. For both projects, follow the 'Register your app with Firebase' steps in");
    System.out.println("     https://firebase.google.com/docs/cloud-messaging/android/client#register_your_app_with_firebase");
    System.out.println("     * Your Android package name for your staging project will end in '.staging', like com.diskuv.communicator.staging");
    System.out.println("     * Do not enter anything for 'Debug signing certificate SHA-1'");
    System.out.println("  2. For both projects, click [Download google-services.json] to obtain your Firebase Android config file (google-services.json)");
    System.out.println("  3. For both projects, go into your Project settings and:");
    System.out.println("     * Set your _first_ SHA certificate fingerprint to be your **release** signing key. This is to reduce chances");
    System.out.println("       of accidental deletion.");
    System.out.println("     * For the staging project only, include any debug SHA fingerprints (one per developer machine)");
    System.out.println("     * There is a little \"?\" button to give you help getting your SHA fingerprints");
    System.out.println("  4. Change Diskuv-Communicator-Android so that src/(main|staging)/res/values/firebase_messaging.xml has the following values:");
    System.out.println("     firebase_messaging.xml             google-services.json");
    System.out.println("     ----------------------             --------------------");
    System.out.println("     google_app_id                      client/client_info/mobilesdk_app_id");
    System.out.println("     default_web_client_id              client/oauth_client/client_id (client_type == 3)");
    System.out.println("     google_api_key                     client/client_info/mobilesdk_app_id");
    System.out.println("     google_crash_reporting_api_key     client/client_info/mobilesdk_app_id");
    System.out.println("     project_id                         project_info/project_id");
    System.out.println("     firebase_database_url              project_info/firebase_url (but you do not need this)");
    System.out.println("Examples:");
    System.out.println("* https://github.com/signalapp/Signal-Android/blob/264a245d27912a3167dee6da531d6b0d41bbe421/app/src/main/res/values/firebase_messaging.xml#L3-L9");
    System.out.println("Reference:");
    System.out.println("* https://developers.google.com/android/guides/google-services-plugin");
    System.out.println();

    return 0;
  }
}
