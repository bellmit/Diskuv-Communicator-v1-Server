/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.websocket.logging.layout.converters;

import org.whispersystems.websocket.logging.WebsocketEvent;

public class LineSeparatorConverter extends WebSocketEventConverter {
  public LineSeparatorConverter() {
  }

  public String convert(WebsocketEvent event) {
    // The conventional protocol for Signal servers is newline, because Signal servers
    // run on UNIX and "\n" is the value of ch.qos.logback.core.CoreConstants.LINE_SEPARATOR.
    // Shouldn't care about UNIX vs Windows in a network protocol, so we will be explicit.
    // The unit tests agree.
    return "\n";
  }
}
