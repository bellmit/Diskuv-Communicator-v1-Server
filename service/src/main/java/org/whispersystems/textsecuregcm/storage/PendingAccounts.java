/*
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.storage;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import java.util.Optional;
import com.google.common.annotations.VisibleForTesting;
import org.whispersystems.textsecuregcm.auth.StoredVerificationCode;
import org.whispersystems.textsecuregcm.storage.mappers.StoredVerificationCodeRowMapper;
import org.whispersystems.textsecuregcm.util.Constants;

import java.util.UUID;

/**
 * Pending accounts used for skipping CAPTCHA requirements with a "verification_code" during initial registration.
 * Unlike the original Signal version, the "number" is actually the deterministic account UUID based on email address,
 * rather than the plaintext phone number. [Diskuv Change] [class]
 */
public class PendingAccounts {

  private final MetricRegistry metricRegistry        = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Timer          insertTimer           = metricRegistry.timer(name(PendingAccounts.class, "insert"          ));
  private final Timer getCodeForPendingAccountTimer  = metricRegistry.timer(name(PendingAccounts.class, "getCodeForPendingAccount"));
  private final Timer          removeTimer           = metricRegistry.timer(name(PendingAccounts.class, "remove"          ));
  private final Timer          vacuumTimer           = metricRegistry.timer(name(PendingAccounts.class, "vacuum"          ));

  private final FaultTolerantDatabase database;

  public PendingAccounts(FaultTolerantDatabase database) {
    this.database = database;
    this.database.getDatabase().registerRowMapper(new StoredVerificationCodeRowMapper());
  }

  @VisibleForTesting
  public void insert(UUID accountUuid, String verificationCode, long timestamp, String pushCode) {
    insert(accountUuid, verificationCode, timestamp, pushCode, null);
  }

  public void insert(UUID accountUuid, String verificationCode, long timestamp, String pushCode, String twilioVerificationSid) {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = insertTimer.time()) {
        handle.createUpdate("INSERT INTO pending_accounts (number, verification_code, timestamp, push_code, twilio_verification_sid) " +
                                "VALUES (:number, :verification_code, :timestamp, :push_code, :twilio_verification_sid) " +
                                "ON CONFLICT(number) DO UPDATE " +
                                "SET verification_code = EXCLUDED.verification_code, timestamp = EXCLUDED.timestamp, push_code = EXCLUDED.push_code, twilio_verification_sid = EXCLUDED.twilio_verification_sid")
              .bind("verification_code", verificationCode)
              .bind("timestamp", timestamp)
              .bind("number", accountUuid.toString())
              .bind("push_code", pushCode)
              .bind("twilio_verification_sid", twilioVerificationSid)
              .execute();
      }
    }));
  }

  public Optional<StoredVerificationCode> getCodeForPendingAccount(UUID accountUuid) {
    return database.with(jdbi ->jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getCodeForPendingAccountTimer.time()) {
        return handle.createQuery("SELECT verification_code, timestamp, push_code, twilio_verification_sid FROM pending_accounts WHERE number = :number")
                     .bind("number", accountUuid.toString())
                     .mapTo(StoredVerificationCode.class)
                     .findFirst();
      }
    }));
  }

  public void remove(UUID accountUuid) {
    database.use(jdbi-> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = removeTimer.time()) {
        handle.createUpdate("DELETE FROM pending_accounts WHERE number = :number")
              .bind("number", accountUuid.toString())
              .execute();
      }
    }));
  }

  public void vacuum() {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = vacuumTimer.time()) {
        handle.execute("VACUUM pending_accounts");
      }
    }));
  }



}
