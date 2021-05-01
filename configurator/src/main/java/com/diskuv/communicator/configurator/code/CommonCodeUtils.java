package com.diskuv.communicator.configurator.code;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwk.UrlJwkProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CommonCodeUtils {
  public static final String        JWKS_JSON              = "jwks.json";
  public static final String        SOURCE_URL             = "source.url";
  public static final ObjectMapper  OBJECT_MAPPER          = new ObjectMapper();
  public static final ObjectWriter  OBJECT_WRITER          = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
  private static final HashFunction JWKS_URL_HASH_FUNCTION = Hashing.sha256();
  private static final Charset      JWKS_URL_ENCODING      = StandardCharsets.UTF_8;

  public static String getUrlHash(String url) {
    return JWKS_URL_HASH_FUNCTION.hashString(url, JWKS_URL_ENCODING).toString();
  }

  public static <T> T downloadUrl(String url, JwksCallback<T> callback) throws IOException {
    URL                 u = new URL(url);
    final URLConnection c = u.openConnection();
    c.setRequestProperty("Accept", "application/json");
    try (InputStream jsonInputStream = c.getInputStream()) {
      JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonInputStream);
      return callback.run(jsonNode);
    }
  }

  public static void validateUrlForParsing(String url) throws IOException, SigningKeyNotFoundException, InvalidPublicKeyException {
    URL            u   = new URL(url);
    UrlJwkProvider ujp = new UrlJwkProvider(u);
    List<Jwk>      all = ujp.getAll();
    for (Jwk jwk : all) {
      jwk.getPublicKey();
    }
  }

  @FunctionalInterface
  interface JwksCallback<T> {
    T run(JsonNode jsonNode) throws IOException;
  }
}
