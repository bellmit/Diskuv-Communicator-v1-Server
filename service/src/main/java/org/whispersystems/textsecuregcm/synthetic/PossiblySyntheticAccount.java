package org.whispersystems.textsecuregcm.synthetic;

import org.whispersystems.textsecuregcm.storage.Account;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PossiblySyntheticAccount {
    /**
     * Only use this method if the Android/iOS client will not be able to tell you are using a real account
     * who is ignoring you.
     *
     * Example: When you are sending a message to an account, it is safe to use this message. The Android/iOS
     * client will not be able to know that you sent a message to a non-existent person (a synthetic account)
     * or someone who does not "Accept" your message requests.
     */
    Optional<Account> getRealAccount();

    UUID getUuid();

    Collection<? extends PossiblySyntheticDevice> getDevices();

    Optional<? extends PossiblySyntheticDevice> getDevice(long deviceId);

    boolean isEnabled();

    Optional<byte[]> getUnidentifiedAccessKey();

    Optional<? extends PossiblySyntheticDevice> getAuthenticatedDevice();

    String getIdentityKey();

    String getProfileName();

    String getProfileEmailAddress();

    String getAvatar();

    boolean isUnrestrictedUnidentifiedAccess();

    boolean isGroupsV2Supported();

    boolean isGv1MigrationSupported();

    Optional<String> getCurrentProfileVersion();
}
