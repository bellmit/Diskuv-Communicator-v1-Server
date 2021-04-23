package org.whispersystems.textsecuregcm.auth;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.whispersystems.textsecuregcm.crypto.Curve;
import org.whispersystems.textsecuregcm.crypto.ECPrivateKey;
import org.whispersystems.textsecuregcm.entities.MessageProtos.SenderCertificate;
import org.whispersystems.textsecuregcm.entities.MessageProtos.ServerCertificate;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class CertificateGenerator {

  private final ECPrivateKey      privateKey;
  private final int               expiresDays;
  private final ServerCertificate serverCertificate;

  public CertificateGenerator(byte[] serverCertificate, ECPrivateKey privateKey, int expiresDays)
      throws InvalidProtocolBufferException
  {
    // [Diskuv Change] Add precondition validation
    Preconditions.checkArgument(
        expiresDays >= 1,
        "The expiry days must be at least 1 day (which is the Android/iOS regeneration period)");
    this.privateKey        = privateKey;
    this.expiresDays       = expiresDays;
    this.serverCertificate = ServerCertificate.parseFrom(serverCertificate);
  }

  public byte[] createFor(Account account, Device device, boolean includeUuid) throws IOException, InvalidKeyException {
    // [Diskuv Change] Use email-based UUID rather than number.
    Preconditions.checkArgument(includeUuid, "Must include the UUID in certificates");
    SenderCertificate.Certificate.Builder builder = SenderCertificate.Certificate.newBuilder()
                                                                                 // WAS: .setSender(account.getNumber())
                                                                                 .setSenderDevice(Math.toIntExact(device.getId()))
                                                                                 .setExpires(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expiresDays))
                                                                                 .setIdentityKey(ByteString.copyFrom(Base64.getDecoder().decode(account.getIdentityKey())))
                                                                                 .setSigner(serverCertificate)
                                                                                 .setSenderUuid(account.getUuid().toString());

    builder.setSenderUuid(account.getUuid().toString());

    byte[] certificate = builder.build().toByteArray();
    byte[] signature   = Curve.calculateSignature(privateKey, certificate);

    return SenderCertificate.newBuilder()
                            .setCertificate(ByteString.copyFrom(certificate))
                            .setSignature(ByteString.copyFrom(signature))
                            .build()
                            .toByteArray();
  }

}
