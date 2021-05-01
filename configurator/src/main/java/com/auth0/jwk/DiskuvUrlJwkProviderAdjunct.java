package com.auth0.jwk;

import java.net.URL;

public class DiskuvUrlJwkProviderAdjunct {
  public static URL getUrl(UrlJwkProvider urlJwkProvider) {
    return urlJwkProvider.url;
  }
}
