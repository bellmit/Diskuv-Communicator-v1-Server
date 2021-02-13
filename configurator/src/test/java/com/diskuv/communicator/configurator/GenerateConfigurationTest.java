package com.diskuv.communicator.configurator;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.whispersystems.curve25519.JavaCurve25519Provider;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.crypto.ECPublicKey;
import org.whispersystems.textsecuregcm.entities.MessageProtos;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.util.ByteUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Map;
import java.util.Random;

import static com.diskuv.communicator.configurator.ConfigurationUtils.convertToYaml;
import static com.diskuv.communicator.configurator.ConfigurationUtils.createConfigurationBuilder;
import static org.assertj.core.api.Assertions.assertThat;

public class GenerateConfigurationTest {
  private static Yaml YAML = new Yaml();
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void
      givenNoOptions__whenCreateWhisperServerConfiguration__thenValidWhisperServerConfiguration()
          throws InvalidKeyException, IllegalAccessException, IOException, ConfigurationException {
    // given: no options
    Random random = new Random(77);
    GenerateConfiguration generateConfiguration = new GenerateConfiguration(random);
    generateConfiguration.serverCertificateSigningKeyPairFile =
        tempFolder.newFile("unit-test-output.pem");

    // when: createWhisperServerConfiguration
    WhisperServerConfiguration whisperServerConfiguration =
        generateConfiguration.createWhisperServerConfiguration();

    // then: valid whisperServerConfiguration ...
    // ... can write to YAML, and ...
    String configYaml = convertToYaml(whisperServerConfiguration);
    // ... can re-read from YAML into a Dropwizard instantiated WhisperServerConfiguration
    ConfigurationSourceProvider csp =
        str -> new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    createConfigurationBuilder().build(csp, configYaml);
  }

  @Test
  public void
      givenHttpsConnection__whenCreateWhisperServerConfiguration__thenValidWhisperServerConfiguration()
          throws InvalidKeyException, IllegalAccessException, IOException, ConfigurationException {
    // given: https connection
    Random random = new Random(77);
    GenerateConfiguration generateConfiguration = new GenerateConfiguration(random);
    generateConfiguration.serverCertificateSigningKeyPairFile =
        tempFolder.newFile("unit-test-output.pem");
    generateConfiguration.applicationConnection = new GenerateConfiguration.ApplicationConnection();
    generateConfiguration.applicationConnection.httpsConnection =
        new GenerateConfiguration.ApplicationHttpsConnection();
    generateConfiguration.applicationConnection.httpsConnection.httpsPort = 9443;
    generateConfiguration.applicationConnection.httpsConnection.keystoreFile = new File(".");
    generateConfiguration.applicationConnection.httpsConnection.keystorePassword = "something";

    // when: createWhisperServerConfiguration
    WhisperServerConfiguration whisperServerConfiguration =
        generateConfiguration.createWhisperServerConfiguration();

    // then: valid whisperServerConfiguration ...
    // ... can write to YAML, and ...
    String configYaml = convertToYaml(whisperServerConfiguration);
    // ... can re-read from YAML into a Dropwizard instantiated WhisperServerConfiguration
    ConfigurationSourceProvider csp =
        str -> new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    createConfigurationBuilder().build(csp, configYaml);
  }

  @Test
  public void
      givenServerCertificateSigningKeyPair__thenPairCoheres__givenServerKeyPair__whenSign__thenValidateSignature__thenSignedCertificateCannotBeRecreated()
          throws IOException, InvalidKeyException {
    // given serverCertificateSigningKeyPairFile
    try (InputStream signingKeyPairStream =
            getClass().getResourceAsStream("serverCertificateSigningKeyPair.pem");
        PemReader reader =
            new PemReader(new InputStreamReader(signingKeyPairStream, StandardCharsets.UTF_8))) {
      PemObject pemPublic = reader.readPemObject();
      assertThat(pemPublic.getType()).isEqualTo("PUBLIC KEY");
      ECPublicKey signingPublicKey = Curve.decodePoint(pemPublic.getContent(), 0);

      PemObject pemPrivate = reader.readPemObject();
      assertThat(pemPrivate.getType()).isEqualTo("PRIVATE KEY");
      ECPrivateKey signingPrivateKey = Curve.decodePrivatePoint(pemPrivate.getContent());

      // then: signing public key derives from signing private key (they are coherent)
      ECPublicKey expectedSigningPublicKey = constructPublicKeyFromPrivateKey(signingPrivateKey);
      assertThat(expectedSigningPublicKey).isEqualTo(signingPublicKey);

      // given: server key pair
      try (InputStream serverKeyPairStream =
          getClass().getResourceAsStream("serverCertificate.yaml")) {
        Map<String, Map<String, String>> config = YAML.load(serverKeyPairStream);
        Map<String, String> unidentifiedDelivery = config.get("unidentifiedDelivery");
        byte[] expectedSignedCertificate =
            Base64.decodeWithoutPadding(unidentifiedDelivery.get("certificate"));
        byte[] privateKey = Base64.decodeWithoutPadding(unidentifiedDelivery.get("privateKey"));
        ECPrivateKey serverPrivateKey = Curve.decodePrivatePoint(privateKey);

        // get ECPublicKey from ECPrivateKey
        ECPublicKey serverPublicKey = constructPublicKeyFromPrivateKey(serverPrivateKey);

        // when: sign
        byte[] certificate =
            MessageProtos.ServerCertificate.Certificate.newBuilder()
                .setId(0)
                .setKey(ByteString.copyFrom(serverPublicKey.serialize()))
                .build()
                .toByteArray();
        byte[] signature = Curve.calculateSignature(serverPrivateKey, certificate);

        // then: validate certificate is signed by certificate authority (aka. signing key)
        Curve.verifySignature(signingPublicKey, certificate, signature);

        // then: signature CANNOT be recreated, since it has random sources
        byte[] signedCertificate =
            MessageProtos.ServerCertificate.newBuilder()
                .setCertificate(ByteString.copyFrom(certificate))
                .setSignature(ByteString.copyFrom(signature))
                .build()
                .toByteArray();
        assertThat(signedCertificate).hasSameSizeAs(expectedSignedCertificate);
        assertThat(ImmutableList.of(signedCertificate))
            .noneSatisfy(c -> assertThat(c).containsSequence(expectedSignedCertificate));
      }
    }
  }

  private static ECPublicKey constructPublicKeyFromPrivateKey(ECPrivateKey privateKey) throws InvalidKeyException {
    JavaCurve25519Provider curve25519Provider = new JavaCurve25519Provider() {};
    byte[] publicKeyRaw = curve25519Provider.generatePublicKey(privateKey.serialize());
    byte[] curveType = {Curve.DJB_TYPE};
    byte[] publicKey = ByteUtil.combine(curveType, publicKeyRaw);
    ECPublicKey serverPublicKey = Curve.decodePoint(publicKey, 0);
    return serverPublicKey;
  }
}
