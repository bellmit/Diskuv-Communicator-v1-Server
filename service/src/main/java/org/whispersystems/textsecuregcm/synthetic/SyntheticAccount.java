package org.whispersystems.textsecuregcm.synthetic;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.textsecuregcm.controllers.DeviceController;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
  // Do not change any of these or else someone can detect whether another person is real (assuming, big if, they
  // can guess their account UUID). A change in these magic constants will create a change in synthetic accounts.
  // However, you _can_ change between the alpha and the beta phases, and ask everyone to
  // resubscribe.

  private static final double DEFAULT_EPSILON = 1e-12;
  private static final int DEFAULT_MAX_ITERATIONS = 10000000;

  /**
   * The device list (from 1 to 6) can have holes in it when a device has been deleted. A hole (if
   * any) disappears when a new device is verified. The probability is conditioned on accounts that
   * have _more than one device_.
   */
  private static final double PROBABILITY_OF_DELETED_DEVICE_HOLES_PER_MULTI_DEVICE_ACCOUNT = 0.1;

  private static final double AVERAGE_REAL_DEVICES_PER_ACCOUNT = 1.5;

  // org.whispersystems.libsignal.util.Medium#MAX_VALUE
  static final int MEDIUM_MAX_VALUE = 0xFFFFFF;

  // ---------------
  // OTHER CONSTANTS
  // ---------------

  private static final String DISCRIMINATOR_IDENTITY_KEY = "ik";
  private static final String DISCRIMINATOR_REGISTRATION_ID = "rid";
  private static final String DISCRIMINATOR_SIGNED_PRE_KEY_ID = "spi";
  private static final String DISCRIMINATOR_SIGNED_PRE_KEY_KEYPAIR = "spk";

  private final SyntheticProfileState profileState;
  private final PoissonDistribution realDevicesPerAccountDistribution;

  private final DeterministicSampling sampling;
  private final String                        identityKey;
  private final ECKeyPair                     identityKeyPair;
  private final List<PossiblySyntheticDevice> devices;

  public SyntheticAccount(byte[] sharedEntropyInput, UUID accountUuid) {
    this.profileState = new SyntheticProfileState(sharedEntropyInput, accountUuid);

    // configure security parameters
    String salt = Base64.getEncoder().withoutPadding().encodeToString(sharedEntropyInput) + accountUuid.toString();

    // make random samplers
    realDevicesPerAccountDistribution =
        new PoissonDistribution(
            RandomGeneratorFactory.createRandomGenerator(profileState.getRandom()),
            AVERAGE_REAL_DEVICES_PER_ACCOUNT,
            DEFAULT_EPSILON,
            DEFAULT_MAX_ITERATIONS);
    sampling = new DeterministicSampling(salt);

    // -----------------------
    // Everything that follows must remain in a stable order. Any variation will cause many UUIDs to
    // change!!!
    // -----------------------

    // Make identity key
    // Confer: org.whispersystems.signalservice.internal.util.JsonUtil.IdentityKeySerializer
    identityKeyPair = makeKeyPair(DISCRIMINATOR_IDENTITY_KEY);
    this.identityKey = Base64.getEncoder().withoutPadding().encodeToString(identityKeyPair.getPublicKey().serialize());

    // Make devices
    this.devices = makeDevices();
  }

  @Override
  public UUID getUuid() {
    return profileState.getAccountUuid();
  }

  @Override
  public Optional<String> getCurrentProfileVersion() {
    return Optional.of(profileState.getKeyVersion());
  }

  @Override
  public String getProfileName() {
    return profileState.getName();
  }

  @Override
  public String getProfileEmailAddress() {
    return profileState.getEmailAddress();
  }

  @Override
  public String getAvatar() {
    return profileState.getAvatar();
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
  public Collection<? extends PossiblySyntheticDevice> getDevices() {
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
  public boolean isUnrestrictedUnidentifiedAccess() {
    return false;
  }

  @Override
  public boolean isGv1MigrationSupported() {
    return true;
  }

  @Override
  public boolean isGroupsV2Supported() {
    return true;
  }

  private List<PossiblySyntheticDevice> makeDevices() {
    long numDevices = Math.max(1, realDevicesPerAccountDistribution.sample());
    List<PossiblySyntheticDevice> syntheticDevices = new ArrayList<>();
    for (long candidateDeviceId = Device.MASTER_ID;
        candidateDeviceId <= DeviceController.MAX_DEVICES && candidateDeviceId <= numDevices;
        ++candidateDeviceId) {
      // Simulate hole due to deletion
      if (candidateDeviceId != Device.MASTER_ID) {
        if (profileState.getRandom().nextDouble() < PROBABILITY_OF_DELETED_DEVICE_HOLES_PER_MULTI_DEVICE_ACCOUNT) {
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

    String signature = Base64.getEncoder().withoutPadding().encodeToString(signatureBytes);
    String signedPreKeyPublicKeyEncoded = Base64.getEncoder().withoutPadding().encodeToString(signedPreKeyPublicKeyBytesWithType);
    return new SignedPreKey(signedPreKeyId, signedPreKeyPublicKeyEncoded, signature);
  }

  private ECKeyPair makeKeyPair(String discriminator) {
    return sampling.deterministicSampledKeyPair(discriminator);
  }
}
