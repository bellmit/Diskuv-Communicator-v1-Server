package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import org.signal.zkgroup.auth.ServerZkAuthOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.CertificateGenerator;
import org.whispersystems.textsecuregcm.entities.DeliveryCertificate;
import org.whispersystems.textsecuregcm.entities.GroupCredentials;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.util.Util;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/certificate")
public class CertificateController {

  private final Logger logger = LoggerFactory.getLogger(CertificateController.class);

  private final CertificateGenerator   certificateGenerator;
  private final ServerZkAuthOperations serverZkAuthOperations;
  private final boolean                isZkEnabled;

  public CertificateController(CertificateGenerator certificateGenerator, ServerZkAuthOperations serverZkAuthOperations, boolean isZkEnabled) {
    this.certificateGenerator   = certificateGenerator;
    this.serverZkAuthOperations = serverZkAuthOperations;
    this.isZkEnabled            = isZkEnabled;
  }

  // As of Signal 5.4.9, the HTTP parameter is includeE164 and not includeUuid. We ignore both HTTP parameters
  // because we _always_ include UUID and _never_ include the non-existent E164.
  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/delivery")
  public DeliveryCertificate getDeliveryCertificate(@Auth Account account)
      throws IOException, InvalidKeyException
  {
    if (account.getAuthenticatedDevice().isEmpty()) {
      throw new AssertionError();
    }

    return new DeliveryCertificate(certificateGenerator.createFor(account, account.getAuthenticatedDevice().get(), true));
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/group/{startRedemptionTime}/{endRedemptionTime}")
  public GroupCredentials getAuthenticationCredentials(@Auth Account account,
                                                       @PathParam("startRedemptionTime") int startRedemptionTime,
                                                       @PathParam("endRedemptionTime") int endRedemptionTime)
  {
    if (!isZkEnabled)                                         throw new WebApplicationException(Response.Status.NOT_FOUND);
    if (startRedemptionTime > endRedemptionTime)              throw new WebApplicationException(Response.Status.BAD_REQUEST);
    if (endRedemptionTime > Util.currentDaysSinceEpoch() + 7) throw new WebApplicationException(Response.Status.BAD_REQUEST);
    if (startRedemptionTime < Util.currentDaysSinceEpoch())   throw new WebApplicationException(Response.Status.BAD_REQUEST);

    List<GroupCredentials.GroupCredential> credentials = new LinkedList<>();

    for (int i=startRedemptionTime;i<=endRedemptionTime;i++) {
      credentials.add(new GroupCredentials.GroupCredential(serverZkAuthOperations.issueAuthCredential(account.getUuid(), i)
                                                                                 .serialize(),
                                                           i));
    }

    return new GroupCredentials(credentials);
  }

}
