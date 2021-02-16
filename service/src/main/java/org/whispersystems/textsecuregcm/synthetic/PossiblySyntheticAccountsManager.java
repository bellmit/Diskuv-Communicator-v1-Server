package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;

import java.util.Optional;
import java.util.UUID;

/**
 * An accounts manager whose central innovation is to always return an account, regardless if it
 * really exists.
 */
public class PossiblySyntheticAccountsManager {
  private final AccountsManager accountsManager;
  private final byte[] sharedEntropyInput;

  public PossiblySyntheticAccountsManager(AccountsManager accountsManager, byte[] sharedEntropyInput) {
    this.accountsManager = accountsManager;
    this.sharedEntropyInput = sharedEntropyInput;
  }

  public void update(PossiblySyntheticAccount account) {
    account.getRealAccount().ifPresent(accountsManager::update);
  }

  /** Unlike {@link AccountsManager#get(UUID)}, we always return an account. */
  public PossiblySyntheticAccount get(UUID accountUuid) {
    Optional<Account> account = accountsManager.get(accountUuid);
    if (account.isPresent()) {
      return account.get();
    }
    return new SyntheticAccount(sharedEntropyInput, accountUuid);
  }
}
