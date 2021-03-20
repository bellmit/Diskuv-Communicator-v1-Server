package org.whispersystems.textsecuregcm.auth;

import com.diskuv.communicatorservice.auth.DiskuvDeviceCredentials;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import io.dropwizard.auth.Authenticator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;

import java.util.Optional;

public class DisabledPermittedDiskuvAccountAuthenticator extends BaseDiskuvAccountAuthenticator implements Authenticator<DiskuvDeviceCredentials, DisabledPermittedAccount> {

  public DisabledPermittedDiskuvAccountAuthenticator(AccountsManager accountsManager, JwtAuthentication jwtAuthentication) {
    super(accountsManager, jwtAuthentication);
  }

  @Override
  public Optional<DisabledPermittedAccount> authenticate(DiskuvDeviceCredentials credentials) {
    Optional<Account> account = super.authenticate(credentials, false);
    return account.map(DisabledPermittedAccount::new);
  }
}
