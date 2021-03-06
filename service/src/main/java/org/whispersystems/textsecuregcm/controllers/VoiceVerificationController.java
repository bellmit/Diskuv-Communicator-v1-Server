package org.whispersystems.textsecuregcm.controllers;

import java.util.Collections;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.whispersystems.textsecuregcm.util.Util;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/voice/")
public class VoiceVerificationController {

  private static final String PLAY_TWIML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<Response>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Pause length=\"1\"/>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Pause length=\"1\"/>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "    <Play>%s</Play>\n" +
      "</Response>";

  private static final String DEFAULT_LOCALE = "en-US";


  private final String      baseUrl;
  private final Set<String> supportedLocales;

  public VoiceVerificationController(String baseUrl, Set<String> supportedLocales) {
    this.baseUrl          = baseUrl;
    this.supportedLocales = supportedLocales;
  }

  @POST
  @Path("/description/{code}")
  @Produces(MediaType.APPLICATION_XML)
  public Response getDescription(@PathParam("code") String code, @QueryParam("l") List<String> locales) {
    code = code.replaceAll("[^0-9]", "");

    if (code.length() != 6) {
      return Response.status(400).build();
    }

    if (locales == null) {
      locales = Collections.emptyList();
    }

    final List<LanguageRange> priorityList;
    try {
      priorityList = locales.stream()
          .map(LanguageRange::new)
          .collect(Collectors.toList());
    } catch (final IllegalArgumentException e) {
      return Response.status(400).build();
    }

    final String localeMatch = Util.findBestLocale(priorityList, supportedLocales).orElse(DEFAULT_LOCALE);

    return getLocalizedDescription(code, localeMatch);
  }

  private Response getLocalizedDescription(String code, String locale) {
    String path = constructUrlForLocale(baseUrl, locale);

    return Response.ok()
                   .entity(String.format(PLAY_TWIML,
                                         path + "verification.wav",
                                         path + code.charAt(0) + "_middle.wav",
                                         path + code.charAt(1) + "_middle.wav",
                                         path + code.charAt(2) + "_middle.wav",
                                         path + code.charAt(3) + "_middle.wav",
                                         path + code.charAt(4) + "_middle.wav",
                                         path + code.charAt(5) + "_falling.wav",
                                         path + "verification.wav",
                                         path + code.charAt(0) + "_middle.wav",
                                         path + code.charAt(1) + "_middle.wav",
                                         path + code.charAt(2) + "_middle.wav",
                                         path + code.charAt(3) + "_middle.wav",
                                         path + code.charAt(4) + "_middle.wav",
                                         path + code.charAt(5) + "_falling.wav",
                                         path + "verification.wav",
                                         path + code.charAt(0) + "_middle.wav",
                                         path + code.charAt(1) + "_middle.wav",
                                         path + code.charAt(2) + "_middle.wav",
                                         path + code.charAt(3) + "_middle.wav",
                                         path + code.charAt(4) + "_middle.wav",
                                         path + code.charAt(5) + "_falling.wav"))
                   .build();
  }

  private String constructUrlForLocale(String baseUrl, String locale) {
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }

    return baseUrl + locale + "/";
  }

}
