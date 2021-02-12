package com.diskuv.communicator.configurator;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Random;

import static com.diskuv.communicator.configurator.ConfigurationUtils.convertToYaml;
import static com.diskuv.communicator.configurator.ConfigurationUtils.createConfigurationBuilder;

public class GenerateConfigurationTest {
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
}
