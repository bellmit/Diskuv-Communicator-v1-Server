package org.whispersystems.textsecuregcm.tests.util;

import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.UUID;

public class UuidHelpers {
    public static final String EMAIL_ALICE   = "alice@test.com";
    public static final String EMAIL_BOB     = "bob@test.com";
    public static final String EMAIL_MISSING = "missing@test.com";
    public static final UUID UUID_ALICE      = DiskuvUuidUtil.uuidForEmailAddress(EMAIL_ALICE);
    public static final UUID UUID_BOB        = DiskuvUuidUtil.uuidForEmailAddress(EMAIL_BOB);
    public static final UUID UUID_MISSING    = DiskuvUuidUtil.uuidForEmailAddress(EMAIL_MISSING);
    public static final String UUID_ALICE_STRING   = UUID_ALICE.toString();
    public static final String UUID_BOB_STRING     = UUID_BOB.toString();
    public static final String UUID_MISSING_STRING = UUID_MISSING.toString();
}
