package org.whispersystems.textsecuregcm.auth;

import io.dropwizard.auth.Authenticator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;

import java.util.Optional;

public class DisabledPermittedDiskuvAccountAuthenticator extends BaseDiskuvAccountAuthenticator implements Authenticator<DiskuvCredentials, DisabledPermittedAccount> {

  public DisabledPermittedDiskuvAccountAuthenticator(AccountsManager accountsManager, JwtAuthentication jwtAuthentication) {
    super(accountsManager, jwtAuthentication);
  }

  @Override
  public Optional<DisabledPermittedAccount> authenticate(DiskuvCredentials credentials) {
    Optional<Account> account = super.authenticate(credentials, false);
    return account.map(DisabledPermittedAccount::new);
  }
}
