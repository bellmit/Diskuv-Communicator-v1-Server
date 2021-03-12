package org.whispersystems.textsecuregcm.auth;

import io.dropwizard.auth.Authenticator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;

import java.util.Optional;

public class DiskuvAccountAuthenticator extends BaseDiskuvAccountAuthenticator
    implements Authenticator<DiskuvCredentials, Account> {
  public DiskuvAccountAuthenticator(
      AccountsManager accountsManager, JwtAuthentication jwtAuthentication) {
    super(accountsManager, jwtAuthentication);
  }

  @Override
  public Optional<Account> authenticate(DiskuvCredentials credentials) {
    return super.authenticate(credentials, true);
  }
}
