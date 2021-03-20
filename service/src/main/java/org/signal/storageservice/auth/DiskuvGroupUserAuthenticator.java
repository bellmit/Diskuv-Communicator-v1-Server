package org.signal.storageservice.auth;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.diskuv.communicatorservice.auth.DiskuvRoleCredentials;
import com.diskuv.communicatorservice.auth.JwtAuthentication;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.InvalidRedemptionTimeException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialPresentation;
import org.signal.zkgroup.auth.ServerZkAuthOperations;
import org.signal.zkgroup.groups.GroupPublicParams;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.DiskuvUuidUtil;

import java.util.Optional;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;

public class DiskuvGroupUserAuthenticator implements Authenticator<DiskuvRoleCredentials, GroupUser> {
  private final MetricRegistry metricRegistry                = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          invalidJwtTokenMeter          = metricRegistry.meter(name(getClass(), "authentication", "invalidJwtToken"));
  private final Meter          invalidGroupPresentationMeter = metricRegistry.meter(name(getClass(), "authentication", "invalidGroupPresentation"));

  private final JwtAuthentication      jwtAuthentication;
  private final ServerZkAuthOperations serverZkAuthOperations;

  public DiskuvGroupUserAuthenticator(JwtAuthentication jwtAuthentication, ServerZkAuthOperations serverZkAuthOperations) {
    this.jwtAuthentication      = jwtAuthentication;
    this.serverZkAuthOperations = serverZkAuthOperations;
  }

  @Override
  public Optional<GroupUser> authenticate(DiskuvRoleCredentials roleCredentials) {
    try {
      jwtAuthentication.verifyBearerTokenAndGetEmailAddress(roleCredentials.getBearerToken());
    } catch (IllegalArgumentException iae) {
      invalidJwtTokenMeter.mark();
      return Optional.empty();
    }

    try {
      // Authenticate the user as part of the group
      String encodedGroupPublicKey = roleCredentials.getUsername();
      String encodedPresentation   = roleCredentials.getPassword();

      GroupPublicParams          groupPublicKey = new GroupPublicParams(Hex.decodeHex(encodedGroupPublicKey));
      AuthCredentialPresentation presentation   = new AuthCredentialPresentation(Hex.decodeHex(encodedPresentation));

      serverZkAuthOperations.verifyAuthCredentialPresentation(groupPublicKey, presentation);

      return Optional.of(new GroupUser(ByteString.copyFrom(presentation.getUuidCiphertext().serialize()),
                                       ByteString.copyFrom(groupPublicKey.serialize()),
                                       ByteString.copyFrom(groupPublicKey.getGroupIdentifier().serialize())));

    } catch (DecoderException | VerificationFailedException | InvalidInputException | InvalidRedemptionTimeException e) {
      return Optional.empty();
    }
  }
}
