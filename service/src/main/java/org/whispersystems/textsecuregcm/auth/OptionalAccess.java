package org.whispersystems.textsecuregcm.auth;

import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticAccount;
import org.whispersystems.textsecuregcm.synthetic.PossiblySyntheticDevice;

import javax.annotation.Nonnull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.security.MessageDigest;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OptionalAccess {
  // Diskuv Change: No information that account is missing.
  // We've changed `Optional<Account>   targetAccount` to `Account   targetAccount` to make it
  // easier to demonstrate there are no information leaks that a target account does not exist. Basically,
  // we've forced the caller to do the target does not exist check _before_ entering these methods.

  public static final String UNIDENTIFIED = "Unidentified-Access-Key";

  public static void verify(Optional<Account>                 requestAccount,
                            Optional<Anonymous>               accessKey,
                            @Nonnull PossiblySyntheticAccount targetAccount,
                            String                            deviceSelector)
  {
    try {
      verify(requestAccount, accessKey, targetAccount);

      if (!deviceSelector.equals("*")) {
        long deviceId = Long.parseLong(deviceSelector);

        Optional<? extends PossiblySyntheticDevice> targetDevice = targetAccount.getDevice(deviceId);

        if (targetDevice.isPresent() && targetDevice.get().isEnabled()) {
          return;
        }

        // Diskuv Change: Avoid information leak. Do UNAUTHORIZED rather than NOT_FOUND when an account is not present
        throw new WebApplicationException(Response.Status.UNAUTHORIZED);
      }
    } catch (NumberFormatException e) {
      throw new WebApplicationException(Response.status(422).build());
    }
  }

  public static void verify(Optional<Account>                 requestAccount,
                            Optional<Anonymous>               accessKey,
                            @Nonnull PossiblySyntheticAccount targetAccount)
  {
    // CLAUSE A
    if (requestAccount.isPresent() && true /* WAS: targetAccount.isPresent() */ && targetAccount.isEnabled()) {
      return;
    }

    //noinspection ConstantConditions
    // Original CLAUSE B:
    //    if (requestAccount.isPresent() && (!targetAccount.isPresent() || (targetAccount.isPresent() && !targetAccount.get().isEnabled()))) {
    //      throw new WebApplicationException(Response.Status.NOT_FOUND);
    //    }
    // Becomes:
    //   if (requestAccount.isPresent() && (!true /* WAS: targetAccount.isPresent() */ || (true /* WAS: targetAccount.isPresent() */ && !targetAccount.isEnabled()))) { ... }
    // Which becomes:
    //   if (requestAccount.isPresent() && (false || (!targetAccount.isEnabled()))) { ... }
    // Which becomes:
    //   if (requestAccount.isPresent() && !targetAccount.isEnabled()) { ... }
    // Which is identical to CLAUSE A. So we can skip it entirely (which was precisely why we changed targetAccount to be non-Optional).

    if (accessKey.isPresent() && true /* WAS: targetAccount.isPresent() */ && targetAccount.isEnabled() && targetAccount.isUnrestrictedUnidentifiedAccess()) {
      return;
    }

    if (accessKey.isPresent()                                &&
        true /* WAS: targetAccount.isPresent() */            &&
        targetAccount.getUnidentifiedAccessKey().isPresent() &&
        targetAccount.isEnabled()                            &&
        MessageDigest.isEqual(accessKey.get().getAccessKey(), targetAccount.getUnidentifiedAccessKey().get()))
    {
      return;
    }

    throw new WebApplicationException(Response.Status.UNAUTHORIZED);
  }

}
