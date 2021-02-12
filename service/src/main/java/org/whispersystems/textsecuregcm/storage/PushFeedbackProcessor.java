package org.whispersystems.textsecuregcm.storage;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.whispersystems.textsecuregcm.sqs.DirectoryQueue;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.Util;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

public class PushFeedbackProcessor extends AccountDatabaseCrawlerListener {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          expired        = metricRegistry.meter(name(getClass(), "unregistered", "expired"));
  private final Meter          recovered      = metricRegistry.meter(name(getClass(), "unregistered", "recovered"));

  private final AccountsManager accountsManager;

  public PushFeedbackProcessor(AccountsManager accountsManager) {
    this.accountsManager = accountsManager;
  }

  @Override
  public void onCrawlStart() {}

  @Override
  public void onCrawlEnd(Optional<UUID> toUuid) {}

  @Override
  protected void onCrawlChunk(Optional<UUID> fromUuid, List<Account> chunkAccounts) {
    for (Account account : chunkAccounts) {
      boolean update = false;

      for (Device device : account.getDevices()) {
        if (device.getUninstalledFeedbackTimestamp() != 0 &&
            device.getUninstalledFeedbackTimestamp() + TimeUnit.DAYS.toMillis(2) <= Util.todayInMillis())
        {
          if (device.getLastSeen() + TimeUnit.DAYS.toMillis(2) <= Util.todayInMillis()) {
            String type = "unknown";
            if (!Util.isEmpty(device.getApnId()) && device.getId() == 1) {
              type = "iPhone";
            } else if (!Util.isEmpty(device.getApnId()) && device.getId() != 1) {
              type = "iPad";
            } else if (!Util.isEmpty(device.getGcmId())) {
              type = "Android";
            } else if (device.getFetchesMessages() && "OWA".equals(device.getUserAgent())) {
              type = "Android Tweaker";
            } else if (device.getFetchesMessages()) {
              type = "Desktop";
            }
            device.setUserAgent(type);
            device.setGcmId(null);
            device.setApnId(null);
            device.setVoipApnId(null);
            device.setFetchesMessages(false);
            expired.mark();
          } else {
            device.setUninstalledFeedbackTimestamp(0);
            recovered.mark();
          }

          update = true;
        }
      }

      if (update) {
        accountsManager.update(account);
      }
    }
  }
}
