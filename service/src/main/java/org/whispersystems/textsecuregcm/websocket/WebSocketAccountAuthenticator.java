package org.whispersystems.textsecuregcm.websocket;

import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.whispersystems.textsecuregcm.auth.DiskuvAccountAuthenticator;
import org.whispersystems.textsecuregcm.auth.DiskuvCredentials;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.websocket.auth.WebSocketAuthenticator;

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

    DiskuvCredentials credentials = new DiskuvCredentials(jwtTokens.get(0), Long.parseLong(deviceIds.get(0)), devicePasswords.get(0));
    return new AuthenticationResult<>(accountAuthenticator.authenticate(credentials), true);
  }

}
