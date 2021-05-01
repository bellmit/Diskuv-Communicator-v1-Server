package com.diskuv.communicator.configurator.code;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.diskuv.communicator.configurator.code.CommonCodeUtils.*;

@CommandLine.Command(
    name = "validate-code-config",
    mixinStandardHelpOptions = true,
    description =
        "Validate configuration that is embedded in the Diskuv-Communicator-Server source code.")
public class ValidateCodeConfiguration implements Callable<Integer> {

  public static void main(String... args) {
    int exitCode =
        new CommandLine(new ValidateCodeConfiguration())
            .setExecutionExceptionHandler(new PrintExceptionMessageHandler())
            .execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // Get the url hash directories
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    String jwtKeysResource = "jwtKeys";
    Enumeration<URL> jwtKeys = classLoader.getResources(jwtKeysResource);
    int count = 0;
    while (jwtKeys.hasMoreElements()) {
      URI jwtKeysUri = jwtKeys.nextElement().toURI();

      // Find a Path we can walk
      System.out.println("Considering " + jwtKeysUri + " ...");
      System.out.println();
      Path jwtKeysDirectory;
      FileSystem fileSystem = null;
      try {
        if ("jar".equals(jwtKeysUri.getScheme())) {
          fileSystem = FileSystems.newFileSystem(jwtKeysUri, Map.of(), classLoader);
          jwtKeysDirectory = fileSystem.getPath(jwtKeysResource);
        } else {
          jwtKeysDirectory = Paths.get(jwtKeysUri);
        }

        // Walk through each url hash subdirectory
        try (Stream<Path> walk = Files.walk(jwtKeysDirectory, 1)) {
          List<Path> dirs = walk.collect(Collectors.toList());
          for (Path urlHashDirectory : dirs) {
            // only want subdirectories (aka. subpaths)
            if (urlHashDirectory.equals(jwtKeysDirectory)) {
              continue;
            }
            validateUrlHashDirectory(urlHashDirectory);
          }
        }
      } finally {
        // Stop resource leaks
        if (fileSystem != null) {
          fileSystem.close();
        }
      }
      ++count;
    }

    if (count == 0) {
      throw new Exception(
          "Could not find *any* JSON Web Key Set. Make sure you have generated at least one with 'generate-code-config'");
    }

    System.out.println("Done.");
    return 0;
  }

  private void validateUrlHashDirectory(Path urlHashDirectory)
      throws IOException, InvalidPublicKeyException, SigningKeyNotFoundException {
    System.out.println("Checking " + urlHashDirectory + " ...");
    String directoryName = urlHashDirectory.getFileName().toString();
    String sourceUrl = Files.readString(urlHashDirectory.resolve(SOURCE_URL));
    String urlHash = getUrlHash(sourceUrl);
    Preconditions.checkArgument(
        urlHash.equals(directoryName),
        "The directory name '%s' did not match the hash of '%s' read from '%s'",
        directoryName,
        sourceUrl,
        SOURCE_URL);

    byte[] jwksJsonBytes = Files.readAllBytes(urlHashDirectory.resolve(JWKS_JSON));
    JsonNode jsonNodeExpected = OBJECT_MAPPER.readTree(jwksJsonBytes);
    JsonNode jsonNodeActual = downloadUrl(sourceUrl, (jsonNode) -> jsonNode);

    if (!jsonNodeExpected.equals(jsonNodeActual)) {
      System.out.println(
          "The content in "
              + urlHashDirectory
              + " for "
              + sourceUrl
              + " is out of date. Re-run generate-code-config with a input YAML file that uses "
              + sourceUrl);
      System.out.println("The latest content is actually:");
      System.out.println(OBJECT_WRITER.writeValueAsString(jsonNodeActual));
      throw new IllegalStateException(sourceUrl + " is out of date");
    }

    // validate the JWKS can be parsed
    Path jwksPath = urlHashDirectory.resolve(JWKS_JSON);
    validateUrlForParsing(jwksPath.toUri().toString());

    System.out.println("  Validated " + sourceUrl);
    System.out.println();
  }
}
