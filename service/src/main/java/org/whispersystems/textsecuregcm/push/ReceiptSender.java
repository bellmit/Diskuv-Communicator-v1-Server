package org.whispersystems.textsecuregcm.push;

import com.google.common.base.Preconditions;
import org.whispersystems.textsecuregcm.controllers.NoSuchUserException;
import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.DiskuvUuidType;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ReceiptSender {

  private final PushSender      pushSender;
  private final AccountsManager accountManager;

  public ReceiptSender(AccountsManager accountManager,
                       PushSender      pushSender)
  {
    this.accountManager = accountManager;
    this.pushSender     = pushSender;
  }

  public void sendReceipt(Account source, String destination, long messageId)
      throws NoSuchUserException, NotPushRegisteredException
  {
    DiskuvUuidType sourceType = DiskuvUuidUtil.verifyDiskuvUuid(source.getUuid().toString());
    DiskuvUuidType destType   = DiskuvUuidUtil.verifyDiskuvUuid(destination);
    Preconditions.checkArgument(
        sourceType == destType, "Cannot cross boundary between OUTDOORS and a HOUSE");

    if (source.getUuid().equals(UUID.fromString(destination))) {
      return;
    }

    Account          destinationAccount = getDestinationAccount(destination);
    Set<Device>      destinationDevices = destinationAccount.getDevices();
    // Contact by email address. Not phone number.
    Envelope.Builder message            = Envelope.newBuilder()
                                                  .setSource("") // WAS: .setSource(source.getNumber())
                                                  .setSourceUuid(source.getUuid().toString())
                                                  .setSourceDevice((int) source.getAuthenticatedDevice().get().getId())
                                                  .setTimestamp(messageId)
                                                  .setType(Envelope.Type.RECEIPT);

    if (source.getRelay().isPresent()) {
      message.setRelay(source.getRelay().get());
    }

    for (Device destinationDevice : destinationDevices) {
      pushSender.sendMessage(destinationAccount, destinationDevice, message.build(), false);
    }
  }

  private Account getDestinationAccount(String destination)
      throws NoSuchUserException
  {
    Optional<Account> account = accountManager.get(destination);

    if (!account.isPresent()) {
      throw new NoSuchUserException(destination);
    }

    return account.get();
  }

}
