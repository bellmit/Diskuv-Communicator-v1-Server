package org.whispersystems.textsecuregcm.synthetic;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.apache.commons.text.RandomStringGenerator;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.textsecuregcm.controllers.DeviceController;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.Base64;
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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * We want a deterministic response since a real account involves a database that has persisted
 * identifiers and keys. We also want to make use of server-side entropy so not trivial for clients
 * to do the math to calculate whether the responses are synthetic.
 *
 * <p>Note: This needs to be one of many defenses against bulk contact discovery. It should _not_ be
 * considered foolproof or anything silly like that. It hasn't even been security audited yet.
 */
public class SyntheticAccount implements PossiblySyntheticAccount {
  // ---------------
  // MAGIC CONSTANTS
  // ---------------
  // Do not change any of these or else everyone's UUIDs will change.
  // However, you _can_ change between the alpha and the beta phases, and ask everyone to
  // resubscribe.

  private static final double DEFAULT_EPSILON = 1e-12;
  private static final int DEFAULT_MAX_ITERATIONS = 10000000;
  private static final int NAME_PADDED_LENGTH_1 = 53;
  private static final int NAME_PADDED_LENGTH_2 = 257;

  /**
   * The device list (from 1 to 6) can have holes in it when a device has been deleted. A hole (if
   * any) disappears when a new device is verified. The probability is conditioned on accounts that
   * have _more than one device_.
   */
  private static final double PROBABILITY_OF_DELETED_DEVICE_HOLES_PER_MULTI_DEVICE_ACCOUNT = 0.1;

  private static final double AVERAGE_REAL_DEVICES_PER_ACCOUNT = 1.5;
  // A profile name is Concat(required first name | 0x00 | optional last name).
  private static final double AVERAGE_PROFILE_NAME_LENGTH = 14;
  private static final int MINIMUM_PROFILE_NAME_LENGTH = 2;

  // org.whispersystems.libsignal.util.Medium#MAX_VALUE
  static final int MEDIUM_MAX_VALUE = 0xFFFFFF;

  // ---------------
  // OTHER CONSTANTS
  // ---------------

  private static final String DISCRIMINATOR_IDENTITY_KEY = "ik";
  private static final String DISCRIMINATOR_REGISTRATION_ID = "rid";
  private static final String DISCRIMINATOR_SIGNED_PRE_KEY_ID = "spi";
  private static final String DISCRIMINATOR_SIGNED_PRE_KEY_KEYPAIR = "spk";

  private final Random rnd;
  private final PoissonDistribution realDevicesPerAccountDistribution;
  private final PoissonDistribution profileNameLengthDistribution;
  private final RandomStringGenerator unicodeGenerator;

  private final UUID accountUuid;
  private final String salt;
  private final DeterministicSampling sampling;
  private final byte[] profileKey;
  private final String profileName;
  private final String                        identityKey;
  private final ECKeyPair                     identityKeyPair;
  private final List<PossiblySyntheticDevice> devices;

  public SyntheticAccount(byte[] sharedEntropyInput, UUID accountUuid) {
    // Random number sequences loosely following NIST SP 800-90A Rev. 1:
    // https://doi.org/10.6028/NIST.SP.800-90Ar1
    // * Personalization string == account UUID
    // * Entropy input == salt shared secret
    // * Initial seed  == the same, and knowable from source code (we want repeatable sequences)

    Preconditions.checkArgument(sharedEntropyInput.length >= HmacDrbg.ENTROPY_INPUT_SIZE_BYTES);
    this.accountUuid = accountUuid;

    // configure security parameters
    this.salt = Base64.encodeBytesWithoutPadding(sharedEntropyInput) + accountUuid.toString();

    // configure deterministic random bit generator (drbg)
    byte[] personalizationString = makePersonalizationString(accountUuid);
    HmacDrbg drbg = new HmacDrbg(sharedEntropyInput, personalizationString);

    // make random samplers
    HmacDrbgRandom randomGenerator = new HmacDrbgRandom(drbg);
    realDevicesPerAccountDistribution =
        new PoissonDistribution(
            RandomGeneratorFactory.createRandomGenerator(randomGenerator),
            AVERAGE_REAL_DEVICES_PER_ACCOUNT,
            DEFAULT_EPSILON,
            DEFAULT_MAX_ITERATIONS);
    profileNameLengthDistribution =
        new PoissonDistribution(
            RandomGeneratorFactory.createRandomGenerator(randomGenerator),
            AVERAGE_PROFILE_NAME_LENGTH,
            DEFAULT_EPSILON,
            DEFAULT_MAX_ITERATIONS);
    unicodeGenerator =
        new RandomStringGenerator.Builder().usingRandom(randomGenerator::nextInt).build();
    rnd = randomGenerator;
    sampling = new DeterministicSampling(salt);

    // -----------------------
    // Everything that follows must remain in a stable order. Any variation will cause many UUIDs to
    // change!!!
    // -----------------------

    // Make profile key
    this.profileKey = makeProfileKey();

    // Make encrypted profile name
    this.profileName = makeProfileName();

    // Make identity key
    // Confer: org.whispersystems.signalservice.internal.util.JsonUtil.IdentityKeySerializer
    identityKeyPair = makeKeyPair(DISCRIMINATOR_IDENTITY_KEY);
    this.identityKey = Base64.encodeBytesWithoutPadding(identityKeyPair.getPublicKey().serialize());

    // Make devices
    this.devices = makeDevices();
  }

  @Override
  public UUID getUuid() {
    return accountUuid;
  }

  @Override
  public String getIdentityKey() {
    return identityKey;
  }

  @Override
  public Optional<Account> getRealAccount() {
    return Optional.empty();
  }

  @Override
  public Optional<? extends PossiblySyntheticDevice> getAuthenticatedDevice() {
    if (devices.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(devices.get(0));
  }

  @Override
  public Iterable<? extends PossiblySyntheticDevice> getDevices() {
    return devices;
  }

  @Override
  public Optional<? extends PossiblySyntheticDevice> getDevice(long deviceId) {
    for (PossiblySyntheticDevice device : devices) {
      if (device.getId() == deviceId) {
        return Optional.of(device);
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public Optional<byte[]> getUnidentifiedAccessKey() {
    return Optional.empty();
  }

  @Override
  public String getProfileName() {
    return profileName;
  }

  @Override
  public String getAvatar() {
    return null;
  }

  @Override
  public boolean isUnrestrictedUnidentifiedAccess() {
    return false;
  }

  @Override
  public boolean isGroupsV2Supported() {
    return true;
  }

  @Override public boolean isGv1MigrationSupported() {
    return false;
  }

  private static byte[] makePersonalizationString(UUID accountUuid) {
    byte[] personalizationString = new byte[16];
    ByteBuffer bb = ByteBuffer.wrap(personalizationString);
    bb.putLong(accountUuid.getMostSignificantBits());
    bb.putLong(accountUuid.getLeastSignificantBits());
    return personalizationString;
  }

  private byte[] makeProfileKey() {
    // Confer: com.diskuv.communicator.crypto.ProfileKeyUtil#createNew
    byte[] profileKey = new byte[32];
    rnd.nextBytes(profileKey);
    return profileKey;
  }

  private String makeProfileName() {
    try {
      // make a synthetic plaintext profile name
      int nameLength =
          Math.max(MINIMUM_PROFILE_NAME_LENGTH, profileNameLengthDistribution.sample());
      byte[] input = unicodeGenerator.generate(nameLength).getBytes(StandardCharsets.UTF_8);

      // pad it.
      // a) borrowed from
      // org.whispersystems.signalservice.api.crypto.ProfileCipher#getTargetNameLength
      final int paddedLength;
      if (input.length <= NAME_PADDED_LENGTH_1) {
        paddedLength = NAME_PADDED_LENGTH_1;
      } else {
        paddedLength = NAME_PADDED_LENGTH_2;
      }
      // b) borrowed from org.whispersystems.signalservice.api.crypto.ProfileCipher#encryptName
      byte[] inputPadded = new byte[paddedLength];
      if (input.length > inputPadded.length) {
        throw new IllegalArgumentException("Input is too long: " + new String(input));
      }
      System.arraycopy(input, 0, inputPadded, 0, input.length);

      // encrypt it
      byte[] nonce = new byte[12];
      rnd.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(profileKey, "AES"),
          new GCMParameterSpec(128, nonce));
      byte[] profileName = ByteUtil.combine(nonce, cipher.doFinal(inputPadded));

      // Jackson Json encode it. Same as
      // org.whispersystems.signalservice.internal.push.PushServiceSocket#writeProfile
      return Base64.encodeBytes(profileName);
    } catch (NoSuchAlgorithmException
        | NoSuchPaddingException
        | java.security.InvalidKeyException
        | InvalidAlgorithmParameterException
        | IllegalBlockSizeException
        | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private List<PossiblySyntheticDevice> makeDevices() {
    long numDevices = Math.max(1, realDevicesPerAccountDistribution.sample());
    List<PossiblySyntheticDevice> syntheticDevices = new ArrayList<>();
    for (long candidateDeviceId = Device.MASTER_ID;
        candidateDeviceId <= DeviceController.MAX_DEVICES && candidateDeviceId <= numDevices;
        ++candidateDeviceId) {
      // Simulate hole due to deletion
      if (candidateDeviceId != Device.MASTER_ID) {
        if (rnd.nextDouble() < PROBABILITY_OF_DELETED_DEVICE_HOLES_PER_MULTI_DEVICE_ACCOUNT) {
          ++candidateDeviceId;
          ++numDevices;
          continue;
        }
      }

      // Make (Signal install time) registration id
      final int registrationId = this.makeRegistrationId(candidateDeviceId);

      // Make signed pre key
      final SignedPreKey signedPreKey = this.makeSignedPreKey(candidateDeviceId);

      // Make the device
      syntheticDevices.add(new SyntheticDevice(candidateDeviceId, registrationId, signedPreKey));
    }
    return syntheticDevices;
  }

  private int makeRegistrationId(long deviceId) {
    // Confer: com.diskuv.communicator.registration.service.CodeVerificationRequest#verifyAccount
    return sampling.deterministicSampledIntInRange(DISCRIMINATOR_REGISTRATION_ID + deviceId, 1, 16380 + 1);
  }

  private SignedPreKey makeSignedPreKey(long deviceId) {
    // confer: com.diskuv.communicator.crypto.PreKeyUtil#generateSignedPreKey
    // confer: org.whispersystems.signalservice.api.push.SignedPreKeyEntity.ByteArraySerializer

    long signedPreKeyId =
        sampling.deterministicSampledIntInRange(
            DISCRIMINATOR_SIGNED_PRE_KEY_ID + deviceId, 0, MEDIUM_MAX_VALUE);
    ECKeyPair signedPreKeyKeyPair =
        makeKeyPair(DISCRIMINATOR_SIGNED_PRE_KEY_KEYPAIR + deviceId);
    ECPublicKey signedPreKeyPublicKey = signedPreKeyKeyPair.getPublicKey();
    byte[] signedPreKeyPublicKeyBytesWithType = signedPreKeyPublicKey.serialize();
    byte[] signedPreKeyPublicKeyBytes = ByteString.copyFrom(signedPreKeyPublicKeyBytesWithType).substring(1).toByteArray();
    Preconditions.checkState(signedPreKeyPublicKeyBytesWithType[0] == (byte) Curve.DJB_TYPE);
    Preconditions.checkState(signedPreKeyPublicKeyBytes.length == 32);

    byte[] signatureBytes;
    try {
      signatureBytes = Curve.calculateSignature(
              identityKeyPair.getPrivateKey(), signedPreKeyPublicKeyBytesWithType);
    } catch (InvalidKeyException e) {
      throw new IllegalStateException(e);
    }

    String signature = Base64.encodeBytesWithoutPadding(signatureBytes);
    String signedPreKeyPublicKeyEncoded = Base64.encodeBytesWithoutPadding(signedPreKeyPublicKeyBytesWithType);
    return new SignedPreKey(signedPreKeyId, signedPreKeyPublicKeyEncoded, signature);
  }

  private ECKeyPair makeKeyPair(String discriminator) {
    return sampling.deterministicSampledKeyPair(discriminator);
  }
}
