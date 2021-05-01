package com.diskuv.communicator.configurator.code;

import com.auth0.jwk.DiskuvUrlJwkProviderAdjunct;
import com.auth0.jwk.UrlJwkProvider;
import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.configuration.JwtKeysConfiguration;
import picocli.CommandLine;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static com.diskuv.communicator.configurator.code.CommonCodeUtils.*;
import static com.diskuv.communicator.configurator.dropwizard.ConfigurationUtils.createConfigurationBuilder;

@CommandLine.Command(
    name = GenerateCodeConfiguration.GENERATE_CODE_CONFIG,
    mixinStandardHelpOptions = true,
    description =
        "Generate configuration that will be embedded into the Diskuv-Communicator-Server source code.")
public class GenerateCodeConfiguration implements Callable<Integer> {
  protected static final String GENERATE_CODE_CONFIG = "generate-code-config";

  @CommandLine.Parameters(
      index = "0",
      description = "The input YAML file which will not be modified")
  protected File inputYamlFile;

  @CommandLine.Parameters(
      index = "1",
      description =
          "The pre-existing directory in the source code where the URL_HASH/jwks.json files will be placed. "
              + "Each URL_HASH is the SHA-256 of the full URL, like SHA256(https://YOUR_DOMAIN/.well-known/jwks.json)")
  protected File jwtSourceCodeDirectory;

  public static void main(String... args) {
    int exitCode =
        new CommandLine(new GenerateCodeConfiguration())
            .setExecutionExceptionHandler(new PrintExceptionMessageHandler())
            .execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // Validate args
    if (!jwtSourceCodeDirectory.exists()) {
      System.out.println(
          "The directory in the source code '" + jwtSourceCodeDirectory + "' does not exist");
      return 1;
    }

    // load configuration
    WhisperServerConfiguration config = createConfigurationBuilder().build(inputYamlFile);
    JwtKeysConfiguration jwtKeys = config.getJwtKeys();
    String domain = jwtKeys.getDomain();

    // figure out the directory the JW key set belongs
    UrlJwkProvider urlJwkProvider = new UrlJwkProvider(domain);
    URL url = DiskuvUrlJwkProviderAdjunct.getUrl(urlJwkProvider);
    String urlString = url.toString();
    String urlHash = getUrlHash(urlString);
    Path urlHashDir = jwtSourceCodeDirectory.toPath().resolve(urlHash);
    Files.createDirectories(urlHashDir);

    // download the JWKS
    Path         jwksPath     = urlHashDir.resolve(JWKS_JSON);
    System.out.println("Downloading from " + url + " ...");
    downloadUrl(urlString, (JsonNode jsonNode) -> {
      System.out.println("Formatting JSON, and writing to " + jwksPath + " ...");
      OBJECT_WRITER.writeValue(jwksPath.toFile(), jsonNode);
      return null;
    });

    // validate the JWKS can be parsed
    validateUrlForParsing(jwksPath.toUri().toString());

    // write the URL to a file
    Path sourcePath = urlHashDir.resolve(SOURCE_URL);
    System.out.println("Writing " + sourcePath + " ...");
    Files.writeString(sourcePath, urlString, StandardCharsets.UTF_8);
    System.out.println("Done.");

    return 0;
  }

}
