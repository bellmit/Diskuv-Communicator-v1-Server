/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.metrics;

import org.whispersystems.textsecuregcm.util.Constants;

public class StorageMetrics {
  // [Diskuv Change] Import of groups from storage-service. We'll just piggy back on the metrics of WhisperService
  public static final String NAME = Constants.METRICS_NAME /* WAS: "storage_metrics" */;
}
