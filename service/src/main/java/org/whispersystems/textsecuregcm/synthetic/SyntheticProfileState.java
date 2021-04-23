package org.whispersystems.textsecuregcm.synthetic;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.apache.commons.text.RandomStringGenerator;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.textsecuregcm.util.ByteUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

import static org.whispersystems.textsecuregcm.controllers.ProfileController.generateAvatarObjectName;

public class SyntheticProfileState {
  // ---------------
  // MAGIC CONSTANTS
  // ---------------
  // Do not change any of these or else someone can detect whether another person is real (assuming,
  // big if, they
  // can guess their account UUID). A change in these magic constants will create a change in
  // synthetic profiles.
  // However, you _can_ change between the alpha and the beta phases, and ask everyone to
  // resubscribe.

  private static final double DEFAULT_EPSILON = 1e-12;
  private static final int DEFAULT_MAX_ITERATIONS = 10000000;

  // org.whispersystems.signalservice.api.crypto.ProfileCipher#NAME_PADDED_LENGTH_1
  private static final int NAME_PADDED_LENGTH_1 = 53;
  // org.whispersystems.signalservice.api.crypto.ProfileCipher#NAME_PADDED_LENGTH_2
  private static final int NAME_PADDED_LENGTH_2 = 257;

  // org.whispersystems.signalservice.api.DiskuvSignalServiceAccountManagerAdjunct#EMAIL_ADDRESS_PADDED_LENGTH
  private static final int EMAIL_ADDRESS_PADDED_LENGTH = 320;

  // org.whispersystems.signalservice.api.crypto.ProfileCipher#ABOUT_PADDED_LENGTH_1
  private static final int ABOUT_PADDED_LENGTH_1 = 128;
  // org.whispersystems.signalservice.api.crypto.ProfileCipher#ABOUT_PADDED_LENGTH_2
  private static final int ABOUT_PADDED_LENGTH_2 = 254;
  // org.whispersystems.signalservice.api.crypto.ProfileCipher#ABOUT_PADDED_LENGTH_3
  private static final int ABOUT_PADDED_LENGTH_3 = 512;

  // org.whispersystems.signalservice.api.crypto.ProfileCipher#EMOJI_PADDED_LENGTH
  private static final int EMOJI_PADDED_LENGTH = 32;

  // A profile name is Concat(required first name | 0x00 | optional last name).
  private static final double AVERAGE_PROFILE_NAME_LENGTH = 14;
  private static final int MINIMUM_PROFILE_NAME_LENGTH = 2;

  // https://www.freshaddress.com/blog/long-email-addresses/
  private static final double AVERAGE_PROFILE_EMAIL_ADDRESS_LENGTH = 22;
  private static final int MINIMUM_PROFILE_EMAIL_ADDRESS_LENGTH = 10;

  private static final double AVERAGE_PROFILE_ABOUT_LENGTH = 10;
  private static final int MINIMUM_PROFILE_ABOUT_LENGTH = 0;

  private static final float PERCENT_OF_PROFILES_HAVING_AVATAR = 0.3f;

  private final UUID accountUuid;
  private final Random random;
  private final byte[] keyBytes;
  private final String keyVersion;
  private final byte[] commitment;
  private final PoissonDistribution profileNameLengthDistribution;
  private final PoissonDistribution profileEmailAddressLengthDistribution;
  private final PoissonDistribution profileAboutLengthDistribution;
  private final RandomStringGenerator unicodeGenerator;
  private final String name;
  private final String emailAddress;
  private final String avatar;
  private final String about;
  private final String aboutEmoji;

  public SyntheticProfileState(byte[] sharedEntropyInput, UUID accountUuid) {
    // Random number sequences loosely following NIST SP 800-90A Rev. 1:
    // https://doi.org/10.6028/NIST.SP.800-90Ar1
    // * Personalization string == account UUID + version
    // * Entropy input == salt shared secret
    // * Initial seed  == the same, and knowable from source code (we want repeatable sequences)

    Preconditions.checkArgument(sharedEntropyInput.length >= HmacDrbg.ENTROPY_INPUT_SIZE_BYTES);
    this.accountUuid = accountUuid;

    // configure deterministic random bit generator (drbg)
    byte[] personalizationString = makePersonalizationString(accountUuid);
    HmacDrbg drbg = new HmacDrbg(sharedEntropyInput, personalizationString);
    this.random = new HmacDrbgRandom(drbg);

    // !!!!!!!!!!!!!!!!!!!!!!
    // The order below matters!
    // Place new fields at the bottom so that the existing profiles are not disturbed.
    // !!!!!!!!!!!!!!!!!!!!!!

    // Make profile key
    ProfileKey profileKey = makeProfileKey();
    this.keyBytes = profileKey.serialize();
    this.keyVersion = profileKey.getProfileKeyVersion(accountUuid).serialize();
    this.commitment = profileKey.getCommitment(accountUuid).serialize();

    // make random samplers
    profileNameLengthDistribution =
        new PoissonDistribution(
            RandomGeneratorFactory.createRandomGenerator(random),
            AVERAGE_PROFILE_NAME_LENGTH,
            DEFAULT_EPSILON,
            DEFAULT_MAX_ITERATIONS);
    profileEmailAddressLengthDistribution =
        new PoissonDistribution(
            RandomGeneratorFactory.createRandomGenerator(random),
            AVERAGE_PROFILE_EMAIL_ADDRESS_LENGTH,
            DEFAULT_EPSILON,
            DEFAULT_MAX_ITERATIONS);
    profileAboutLengthDistribution =
        new PoissonDistribution(
            RandomGeneratorFactory.createRandomGenerator(random),
            AVERAGE_PROFILE_ABOUT_LENGTH,
            DEFAULT_EPSILON,
            DEFAULT_MAX_ITERATIONS);
    unicodeGenerator = new RandomStringGenerator.Builder().usingRandom(random::nextInt).build();

    // Make encrypted profile name
    this.name = makeName();

    // Make encrypted profile email address
    this.emailAddress = makeEmailAddress();

    // Make reference to avatar
    this.avatar = makeAvatar();

    // Make about/emoji
    this.about = makeAbout();
    this.aboutEmoji = makeAboutEmoji();
  }

  public UUID getAccountUuid() {
    return accountUuid;
  }

  public String getKeyVersion() {
    return keyVersion;
  }

  public byte[] getCommitment() {
    return commitment.clone();
  }

  public String getName() {
    return name;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public String getAbout() {
    return about;
  }

  public String getAboutEmoji() {
    return aboutEmoji;
  }

  public String getAvatar() {
    return avatar;
  }

  Random getRandom() {
    return random;
  }

  private ProfileKey makeProfileKey() {
    // Confer: com.diskuv.communicator.crypto.ProfileKeyUtil#createNew
    byte[] profileKey = new byte[32];
    random.nextBytes(profileKey);
    try {
      return new ProfileKey(profileKey);
    } catch (InvalidInputException e) {
      throw new IllegalStateException(e);
    }
  }

  private String makeName() {
    // make a synthetic plaintext profile name
    int length = Math.max(MINIMUM_PROFILE_NAME_LENGTH, profileNameLengthDistribution.sample());
    byte[] input = unicodeGenerator.generate(length).getBytes(StandardCharsets.UTF_8);

    final int paddedLength = getProfileNamePaddedLength(input);
    return encryptProfileField(input, paddedLength);
  }

  private String makeEmailAddress() {
    // make a synthetic plaintext profile email address
    int length =
        Math.max(
            MINIMUM_PROFILE_EMAIL_ADDRESS_LENGTH, profileEmailAddressLengthDistribution.sample());
    byte[] input = unicodeGenerator.generate(length).getBytes(StandardCharsets.UTF_8);

    return encryptProfileField(input, EMAIL_ADDRESS_PADDED_LENGTH);
  }

  private String makeAbout() {
    int length = Math.max(MINIMUM_PROFILE_ABOUT_LENGTH, profileAboutLengthDistribution.sample());
    byte[] input = unicodeGenerator.generate(length).getBytes(StandardCharsets.UTF_8);

    final int paddedLength = getProfileAboutPaddedLength(input);
    return encryptProfileField(input, paddedLength);
  }

  private String makeAboutEmoji() {
    byte[] input = new byte[0];
    return encryptProfileField(input, EMOJI_PADDED_LENGTH);
  }

  private String makeAvatar() {
    // Make a synthetic avatar. To actually return an avatar, you either have to get the avatar
    // uploaded -or-
    // provide authorization controls on the avatar S3 object so that only people with the profile
    // key can
    // access it -or- have the CDN edge router synthesize a deterministic avatar ciphertext. No
    // plans to do any
    // of those right now. We'll go through the motions so we draw an appropriate
    // number of bytes from the random generator, to protect us if we implement one of those options
    // (we don't
    // want any other profile fields to change, so keep random generator stable). Until then we
    // always return
    // _no_ avatar.
    float value = random.nextFloat();
    if (value < PERCENT_OF_PROFILES_HAVING_AVATAR) {
      generateAvatarObjectName(bytes -> random.nextBytes(bytes));
      return null;
    }
    // no avatar
    return null;
  }

  private int getProfileNamePaddedLength(byte[] input) {
    // a) borrowed from
    // org.whispersystems.signalservice.api.crypto.ProfileCipher#getTargetNameLength
    final int paddedLength;
    if (input.length <= NAME_PADDED_LENGTH_1) {
      paddedLength = NAME_PADDED_LENGTH_1;
    } else {
      paddedLength = NAME_PADDED_LENGTH_2;
    }
    return paddedLength;
  }

  private int getProfileAboutPaddedLength(byte[] input) {
    // borrowed from
    // org.whispersystems.signalservice.api.crypto.ProfileCipher#getTargetAboutLength
    if (input.length <= ABOUT_PADDED_LENGTH_1) {
      return ABOUT_PADDED_LENGTH_1;
    } else if (input.length < ABOUT_PADDED_LENGTH_2){
      return ABOUT_PADDED_LENGTH_2;
    } else {
      return ABOUT_PADDED_LENGTH_3;
    }
  }

  private String encryptProfileField(byte[] input, int paddedLength) {
    try {
      // pad it.
      // ... borrowed from org.whispersystems.signalservice.api.crypto.ProfileCipher#encryptName
      byte[] inputPadded = new byte[paddedLength];
      if (input.length > inputPadded.length) {
        throw new IllegalArgumentException("Input is too long: " + new String(input));
      }
      System.arraycopy(input, 0, inputPadded, 0, input.length);

      // encrypt it
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(keyBytes, "AES"),
          new GCMParameterSpec(128, nonce));
      byte[] profileName = ByteUtil.combine(nonce, cipher.doFinal(inputPadded));

      // Jackson Json encode it. Same as
      // org.whispersystems.signalservice.internal.push.PushServiceSocket#writeProfile
      return Base64.getEncoder().encodeToString(profileName);
    } catch (NoSuchAlgorithmException
        | NoSuchPaddingException
        | InvalidKeyException
        | InvalidAlgorithmParameterException
        | IllegalBlockSizeException
        | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  static byte[] makePersonalizationString(UUID accountUuid) {
    byte[] personalizationString = new byte[8 + 8];
    ByteBuffer bb = ByteBuffer.wrap(personalizationString);
    bb.putLong(accountUuid.getMostSignificantBits());
    bb.putLong(accountUuid.getLeastSignificantBits());
    return personalizationString;
  }
}
