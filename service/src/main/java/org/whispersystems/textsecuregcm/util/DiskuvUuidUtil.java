package org.whispersystems.textsecuregcm.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * This class must be the same or incorporate fewer UUID features than the copy of this
 * class in the clients (Android/iOS). Said another way, upgrade the clients and migrate
 * them before upgrading this class.
 */
public class DiskuvUuidUtil {
    private static final Pattern E164_VALIDATION = Pattern.compile("[+][0-9]+");

    public static UUID uuidForE164Number(String e164number) {
        // pre-conditions (just a lightweight sanity check)
        if (!E164_VALIDATION.matcher(e164number).matches()) {
            throw new IllegalArgumentException("Does not look like a e164 number");
        }

        // we'll use a random-type UUID (Type 4)
        byte[] uuidType4Bytes = new byte[16];

        // and fill it with 224-bit SHA-224 hash of the phone number digits. Suppose we could have
        // used 160-bit SHA-1 but prefer SHA-2 family. Regardless, UUID is only 128-bits so we need
        // to truncate it.
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-224");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        String e164digits = e164number.substring(1);
        byte[]        shaBytes   = digest.digest(e164digits.getBytes(StandardCharsets.US_ASCII));
        System.arraycopy(shaBytes, 0, uuidType4Bytes, 0, uuidType4Bytes.length);

        // perform same bit-setting as done by UUID.randomUUID()
        uuidType4Bytes[6] &= 0x0f;  /* clear version        */
        uuidType4Bytes[6] |= 0x40;  /* set to version 4     */
        uuidType4Bytes[8] &= 0x3f;  /* clear variant        */
        uuidType4Bytes[8] |= 0x80;  /* set to IETF variant  */

        // future-proofing: we'll reserve two bits for a Diskuv UUID version field
        uuidType4Bytes[0] &= 0x3f; /* clear 2 most significant bits; set to Diskuv UUID version 0 */

        // Status: We started with 128 bits. 4 bits went to UUID version, 2 bits went to UUID variant
        // and 2 bits went to Diskuv UUID version. So at this point 120 bits of SHA hash are left.

        ByteBuffer byteBuffer = ByteBuffer.wrap(uuidType4Bytes);
        long high = byteBuffer.getLong();
        long low = byteBuffer.getLong();

        return new UUID(high, low);
    }
}
