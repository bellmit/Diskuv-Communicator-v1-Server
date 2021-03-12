package org.whispersystems.textsecuregcm.util;

import com.google.common.base.Preconditions;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorContextImpl;

import javax.validation.ConstraintValidatorContext;
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

    public static void verifyDiskuvUuid(String destination) throws IllegalArgumentException {
        UUID uuid = UUID.fromString(destination);
        // UUID version 4
        Preconditions.checkArgument(uuid.version() == 4);
        // variant 1 (IETF variant, which is 10 in binary)
        Preconditions.checkArgument(uuid.variant() == 0b10);
        // Diskuv UUID version 0
        Preconditions.checkArgument((uuid.getMostSignificantBits() & 0xc0000000_00000000L) == 0);
    }

    public static UUID uuidForEmailAddress(String emailAddress) throws IllegalArgumentException {
        // pre-conditions (lightweight sanity check; no external libraries so works on iOS/Android/server/etc.)
        Preconditions.checkArgument(emailAddress != null && emailAddress.length() > 3 &&
                emailAddress.contains("@") && emailAddress.contains("."),
                "Does not look like an email address");

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
        String e164digits = emailAddress.substring(1);
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
