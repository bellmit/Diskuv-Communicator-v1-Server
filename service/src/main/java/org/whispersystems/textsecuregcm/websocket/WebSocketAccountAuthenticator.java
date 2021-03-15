package org.whispersystems.textsecuregcm.websocket;

import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.whispersystems.textsecuregcm.auth.DiskuvAccountAuthenticator;
import org.whispersystems.textsecuregcm.auth.DiskuvCredentials;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.websocket.auth.WebSocketAuthenticator;
import org.whispersystems.websocket.util.Base64;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class WebSocketAccountAuthenticator implements WebSocketAuthenticator<Account> {

  private final DiskuvAccountAuthenticator accountAuthenticator;

  public WebSocketAccountAuthenticator(DiskuvAccountAuthenticator accountAuthenticator) {
    this.accountAuthenticator = accountAuthenticator;
  }

  @Override
  public AuthenticationResult<Account> authenticate(UpgradeRequest request) {
    Map<String, List<String>> parameters = request.getParameterMap();
    // The request parameters are defined in org.whispersystems.signalservice.internal.websocket.WebSocketConnection
    // for Android clients.
    // Format: /v1/websocket/?device-id=%d&device-password=%s&jwt-token=%s
    List<String>              deviceIds  = parameters.get("device-id");
    List<String>              devicePasswords  = parameters.get("device-password");
    List<String>              jwtTokens  = parameters.get("jwt-token");

    if (deviceIds == null || deviceIds.size() != 1 || devicePasswords == null || devicePasswords.size() != 1 || jwtTokens == null || jwtTokens.size() != 1)
    {
      return new AuthenticationResult<>(Optional.empty(), false);
    }

    // -------------------------
    // from here on a valid authentication is required because the user sent the credentials
    final boolean AUTH_IS_REQUIRED = true;

    // validate password
    String encodedDevicePassword = devicePasswords.get(0);
    byte[] devicePassword;
    try {
      devicePassword = Base64.decode(encodedDevicePassword, Base64.URL_SAFE);
    } catch (IOException e) {
      return new AuthenticationResult<>(Optional.empty(), AUTH_IS_REQUIRED);
    }

    // validate device id
    long deviceId;
    try {
      deviceId = Long.parseLong(deviceIds.get(0));
    } catch (NumberFormatException e) {
      return new AuthenticationResult<>(Optional.empty(), AUTH_IS_REQUIRED);
    }

    DiskuvCredentials credentials = new DiskuvCredentials(jwtTokens.get(0), deviceId, devicePassword);
    return new AuthenticationResult<>(accountAuthenticator.authenticate(credentials), AUTH_IS_REQUIRED);
  }

}
