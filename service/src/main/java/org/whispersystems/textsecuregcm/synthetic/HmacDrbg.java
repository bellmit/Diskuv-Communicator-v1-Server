package org.whispersystems.textsecuregcm.synthetic;

import javax.annotation.concurrent.NotThreadSafe;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Deterministic Random Bit Generator based on HMAC-SHA256.
 *
 * <h3>Commentary</h3>
 *
 * Source: Apache 2.0 Licensed https://github.com/google/rappor Committer: Tom Kaitchuck. Commit:
 * d797259
 *
 * <p>This code refers to a withdrawn NIST paper. The new NIST paper is at
 * https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-90Ar1.pdf . This algorithm
 * (HMAC-DRBG) is still in the new NIST paper.
 *
 * <p>beckford@ modified so it doesn't depend on Google Guava. That makes it usable on Android
 * almost verbatim.
 *
 * <h3>Original</h3>
 *
 * Also known as: HMAC_DRBG. See http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf
 * for thorough specification.
 *
 * <p>Reseeding is not supported. Instead, construct a new DRBG when reseeding is required. See
 * http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf Section 8.6.8.
 */
@NotThreadSafe
public class HmacDrbg {
  private static final String ALGORITHM = "HmacSHA256";

  // "V" from the the spec.
  private byte[] value;

  // An instance of HMAC-SHA256 configured with "Key" from the spec.
  private final Mac mac;

  // The total number of bytes that have been generated from this DRBG so far.
  private int bytesGenerated;

  // Assume maximum security strength for HMAC-256, which is 256.
  // See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf D.2 #1.
  public static final int SECURITY_STRENGTH = 256;

  /**
   * Personalization strings should not exceed this many bytes in length.
   *
   * See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf D.2 #7.
   */
  public static final int MAX_PERSONALIZATION_STRING_LENGTH_BYTES = 160 / 8;

  /**
   * The constructor's entropyInput should contain this many high quality random bytes.
   * HMAC_DRBG requires entropy input to be security_strength bits long,
   * and nonce to be at least 1/2 security_strength bits long.  We
   * generate them both as a single "extra strong" entropy input.
   * See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf
   */
  public static final int ENTROPY_INPUT_SIZE_BYTES = (SECURITY_STRENGTH / 8) * 3 / 2;

  /**
   * The maximum total number of bytes that can be generated from this DRBG.
   *
   * This is conservative releative to the suggestions in
   * http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf section D.2,
   * ensuring that reseeding is never triggered (each call to Generate produces at least one byte,
   * therefore MAX_BYTES will be reached before RESEED_INTERAL=10000 is exceeded)
   * and simplifying the interface (so that the client need not worry about MAX_BYTES_PER_REQUEST,
   * below.
   */
  public static final int MAX_BYTES_TOTAL = 10000;

  // See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf D.2 #2.
  private static final int DIGEST_NUM_BYTES = 256 / 8;

  // floor(7500/8); see: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf D.2 #5.
  private static final int MAX_BYTES_PER_REQUEST = 937;

  private static final byte[] BYTE_ARRAY_0 = {0};
  private static final byte[] BYTE_ARRAY_1 = {1};

  public HmacDrbg(byte[] entropyInput, byte[] personalizationString) {
    mac = createMac();

    // HMAC_DRBG Instantiate Process
    // See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf 10.1.1.2

    // 1. seed_material = entropy_input + nonce + personalization_string
    // Note: We are using the 8.6.7 interpretation, where the entropy_input and
    // nonce are acquired at the same time from the same source.
    byte[] seedMaterial = concat(entropyInput, emptyIfNull(personalizationString));

    // 2. Key = 0x00 00...00
    setKey(new byte[256 / 8]);

    // 3. V = 0x01 01...01
    value = new byte[DIGEST_NUM_BYTES];
    Arrays.fill(value, (byte) 0x01);

    // 4. (Key, V) = HMAC_DRBG_Update(seed_material, Key, V)
    hmacDrbgUpdate(seedMaterial);

    bytesGenerated = 0;
  }

  /**
   * Returns an 0-length byte array if b is null, otherwise returns b.
   */
  private static byte[] emptyIfNull(byte[] b) {
    return b == null ? new byte[0] : b;
  }

  /**
   * Set's the "Key" state from the spec.
   */
  private void setKey(byte[] key) {
    try {
      SecretKeySpec hmacKey = new SecretKeySpec(key, ALGORITHM);
      mac.init(hmacKey);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Computes hmac("key" from the spec, x).
   */
  private byte[] hash(byte[] x) {
    return mac.doFinal(x);
  }

  private static Mac createMac() {
    try {
      return Mac.getInstance(ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] concat(byte[]... arrays) {
    int length = 0;
    for (byte[] array : arrays) {
      length += array.length;
    }
    byte[] result = new byte[length];
    int pos = 0;
    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, pos, array.length);
      pos += array.length;
    }
    return result;
  }

  /**
   * HMAC_DRBG Update Process
   *
   * See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf 10.1.2.2
   */
  private void hmacDrbgUpdate(byte[] providedData) {

    // 1. K = HMAC(K, V || 0x00 || provided_data)
    setKey(hash(concat(value, BYTE_ARRAY_0, emptyIfNull(providedData))));

    // 2. V = HMAC(K, V);
    value = hash(value);

    // 3. If (provided_data = Null), then return K and V.
    if (providedData == null) {
      return;
    }

    // 4. K = HMAC (K, V || 0x01 || provided_data).
    setKey(hash(concat(value, BYTE_ARRAY_1, providedData)));

    // 5. V = HMAC (K, V).
    value = hash(value);
  }

  /**
   * HMAC_DRBG Generate Process
   *
   * See: http://csrc.nist.gov/publications/nistpubs/800-90A/SP800-90A.pdf 10.1.2.5
   *
   * We do not support additional_input, assuming it to be always null.
   *
   * We guarantee that reseeding is never required through the use of MAX_BYTES_TOTAL
   * rather than RESEED_INTERVAL.
   */
  private void hmacDrbgGenerate(byte[] out, int start, int count) {
    // 3. temp = Null.
    int bytesWritten = 0;

    // 4. While (len (temp) < requested_number_of_bits) do:
    while (bytesWritten < count) {
      // 4.1 V = HMAC (Key, V).
      value = hash(value);

      // 4.2 temp = temp || V.
      // 5. returned_bits = Leftmost requested_number_of_bits of temp
      int bytesToWrite = Math.min(count - bytesWritten, DIGEST_NUM_BYTES);
      System.arraycopy(value, 0, out, start + bytesWritten, bytesToWrite);
      bytesWritten += bytesToWrite;
    }

    // 6. (Key, V) = HMAC_DRBG_Update (additional_input, Key, V).
    hmacDrbgUpdate(null);
  }

  /**
   * Generates entropy byte-string suitable for use as the constructor's entropyInput.
   *
   * Uses SecureRandom to generate entropy.
   */
  public static byte[] generateEntropyInput() {
    byte[] result = new byte[ENTROPY_INPUT_SIZE_BYTES];
    new SecureRandom().nextBytes(result);
    return result;
  }

  /**
   * Returns the next length pseudo-random bytes.
   */
  public byte[] nextBytes(int length) {
    byte[] result = new byte[length];
    nextBytes(result);
    return result;
  }

  /**
   * Fills the output vector with pseudo-random bytes.
   */
  public void nextBytes(byte[] out) {
    nextBytes(out, 0, out.length);
  }

  /**
   * Fills out[start] through out[start+count-1] (inclusive) with pseudo-random bytes.
   */
  public void nextBytes(byte[] out, int start, int count) {
    if (count == 0) {
      return;
    }
    if (bytesGenerated + count > MAX_BYTES_TOTAL) {
      throw new IllegalStateException("Cannot generate " + (bytesGenerated + count) + " bytes, which is more than a total of " + MAX_BYTES_TOTAL + " bytes.");
    }
    try {
      int bytesWritten = 0;
      while (bytesWritten < count) {
        int bytesToWrite = Math.min(count - bytesWritten, MAX_BYTES_PER_REQUEST);
        hmacDrbgGenerate(out, start + bytesWritten, bytesToWrite);
        bytesWritten += bytesToWrite;
      }
    } finally {
      bytesGenerated += count;
    }
  }
}
