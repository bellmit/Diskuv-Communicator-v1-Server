// Copyright 2021 Diskuv, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.whispersystems.textsecuregcm.util;

import com.google.common.base.Preconditions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * This class must be the same or incorporate fewer UUID features than the copy of this class in the
 * clients (Android/iOS). Said another way, upgrade the clients and migrate them before upgrading
 * this class.
 */
public class DiskuvUuidUtil {
  /**
   * Use SHA-224 to fill the UUID with an email address. An alternative would be
   * to use the 160-bit SHA-1 but we prefer SHA-2 family. Regardless, UUID is only 128-bits so we
   * will need to truncate it.
   */
  private static final String SHA_224      = "SHA-224";
  /**
   * Pick HMAC using same logic as {@link #SHA_224}.
   */
  private static final String HMAC_SHA_224 = "HmacSHA224";

  /**
   * @param emailAddress The email address of the user
   * @return the UUID that is valid for the user outside of a sanctuary
   * @throws IllegalArgumentException
   */
  public static UUID uuidForOutdoorEmailAddress(String emailAddress)
          throws IllegalArgumentException {
    verifyEmailAddress(emailAddress);

    byte[] uuidType4Bytes = generateAllOutdoorsBitsExceptUuidType(emailAddress);

    // Diskuv UUID type 0
    uuidType4Bytes[0] &= 0x3f; /* set 0b00 in the 2 most significant bits */

    return toUuid(uuidType4Bytes);
  }

  /**
   * @param emailAddress            The email address of the user
   * @param sanctuaryGroupMasterKey The group master key for the sanctuary
   * @return the UUID that is specific for that user in the given sanctuary
   * @throws IllegalArgumentException
   */
  public static UUID uuidForSanctuaryEmailAddress(String emailAddress,
                                                  byte[] sanctuaryGroupMasterKey)
          throws IllegalArgumentException {
    verifyEmailAddress(emailAddress);
    Preconditions.checkArgument(sanctuaryGroupMasterKey != null && sanctuaryGroupMasterKey.length == 32);

    byte[] uuidType4Bytes = generateAllSanctuaryBitsExceptUuidType(emailAddress, sanctuaryGroupMasterKey);

    // Diskuv UUID type 1
    uuidType4Bytes[0] &= 0x3f; /* set 0b00 in the 2 most significant bits */
    uuidType4Bytes[0] |= 0x40; /* set 0b01 in the 2 most significant bits */

    return toUuid(uuidType4Bytes);
  }

  /**
   * Fills out a Type 4 UUID. The caller is responsible for setting the Diskuv UUID type into the
   * two most significant bits. At present we don't use all two bits, but they are reserved for
   * future proofing.
   *
   * @return a Type 4 UUID. We started with 128 bits. 4 bits went to UUID version, 2 bits went to
   * UUID variant and 2 bits go to Diskuv UUID version. So at this point 120 bits of SHA hash
   * are left.
   */
  private static byte[] generateAllOutdoorsBitsExceptUuidType(String emailAddress) {
    byte[] emailAddressBytes = emailAddress.getBytes(StandardCharsets.UTF_8);

    // Fill UUID with SHA2(email address)
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(SHA_224);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
    byte[] shaBytes = digest.digest(emailAddressBytes);

    return truncateDigestAndBitTwiddleIntoUUID(shaBytes);
  }

  /**
   * Fills out a Type 4 UUID. The caller is responsible for setting the Diskuv UUID type into the
   * two most significant bits. At present we don't use all two bits, but they are reserved for
   * future proofing.
   *
   * @return a Type 4 UUID. We started with 128 bits. 4 bits went to UUID version, 2 bits went to
   * UUID variant and 2 bits go to Diskuv UUID version. So at this point 120 bits of SHA hash
   * are left.
   */
  private static byte[] generateAllSanctuaryBitsExceptUuidType(String emailAddress,
                                                               byte[] sanctuaryGroupMasterKey) {
    byte[] emailAddressBytes = emailAddress.getBytes(StandardCharsets.UTF_8);

    // Fill UUID with HMAC(key, email address)
    byte[] digest;
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_224);
      mac.init(new SecretKeySpec(sanctuaryGroupMasterKey, "HmacSHA224"));
      digest = mac.doFinal(emailAddressBytes);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }

    // Truncate into uuidType4Bytes
    return truncateDigestAndBitTwiddleIntoUUID(digest);
  }

  /**
   * Truncate the digest into a UUID-sized byte array, and twiddle with the bits to make it a valid Version
   * 4 IETF variant UUID
   */
  private static byte[] truncateDigestAndBitTwiddleIntoUUID(byte[] digest) {
    // Truncate the digest
    byte[] uuidType4Bytes = new byte[16];
    System.arraycopy(digest, 0, uuidType4Bytes, 0, uuidType4Bytes.length);

    // Perform same bit-setting as done by UUID.randomUUID()
    uuidType4Bytes[6] &= 0x0f; /* clear version        */
    uuidType4Bytes[6] |= 0x40; /* set to version 4     */
    uuidType4Bytes[8] &= 0x3f; /* clear variant        */
    uuidType4Bytes[8] |= 0x80; /* set to IETF variant  */

    return uuidType4Bytes;
  }

  public static void verifyEmailAddress(String emailAddress) {
    // pre-conditions (lightweight sanity check; no external libraries so works on
    // iOS/Android/server/etc.).

    // We don't need to do much ... email addresses are
    // untrusted in Diskuv, and have to be verified with a email code.

    // We also don't care about uniqueness; if someone wants to sign up with
    // the same email address encoded differently, then let them as long as they
    // prove ownership of each encoding. To us it will look like multiple accounts,
    // and to other users it will look like multiple email addresses (as long as
    // the characters are PRINTABLE).

    Preconditions.checkArgument(emailAddress != null && emailAddress.equals(emailAddress.trim()),
                                "Does not look like an email address");

    // Verify printable
    for (int i = 0; i < emailAddress.length(); ) {
      int cp = emailAddress.codePointAt(i);
      Preconditions.checkArgument(Character.isDefined(cp) && !Character.isISOControl(cp) && !Character.isWhitespace(cp));
      i += Character.charCount(cp);
    }

    // Split into local part and domain name
    int at = emailAddress.lastIndexOf('@');
    Preconditions.checkArgument(at > 0);

    // Local part. It can be UTF-8 _and_ case-sensitive. Don't touch it except:
    // * Reject any UUCP-style email addresses (the ones with d!b!c!user@xyz) since we will
    //   be using UUCP prefixes to qualify email addresses into sanctuaries.
    String localPart = emailAddress.substring(0, at);
    Preconditions.checkArgument(localPart.length() > 0 && localPart.indexOf('!') < 0);

    // Domain names are case-insensitive, including internationalized domain names (IDN) that are
    // encoded in lowercase ASCII (RFC 3492). We could normalize the domain name to lowercase, but
    // not everyone uses IDN. Just adhere to our standard of "don't care about uniqueness".
    Preconditions.checkArgument(at + 1 < emailAddress.length());
    String domainName = emailAddress.substring(at + 1);
    Preconditions.checkArgument(domainName.indexOf('.') > 0);
  }

  private static UUID toUuid(byte[] uuidType4Bytes) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(uuidType4Bytes);
    long       high       = byteBuffer.getLong();
    long       low        = byteBuffer.getLong();

    return new UUID(high, low);
  }

  public static DiskuvUuidType verifyDiskuvUuid(String possibleDiskuvUuid)
          throws IllegalArgumentException {
    UUID uuid = UUID.fromString(possibleDiskuvUuid);
    // UUID version 4
    Preconditions.checkArgument(uuid.version() == 4);
    // variant 1 (IETF variant, which is 10 in binary)
    Preconditions.checkArgument(uuid.variant() == 0b10);
    // Diskuv UUID type 0 or 1?
    long l = uuid.getMostSignificantBits() >>> 62;
    Preconditions.checkArgument(l == 0 || l == 1);
    return l == 0 ? DiskuvUuidType.OUTDOORS : DiskuvUuidType.SANCTUARY_SPECIFIC;
  }
}
