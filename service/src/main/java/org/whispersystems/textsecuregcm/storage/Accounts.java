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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.whispersystems.textsecuregcm.storage.mappers.AccountRowMapper;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.SystemMapper;

public class Accounts implements AccountStore {

  public static final String ID     = "id";
  public static final String UID    = "uuid";
  public static final String NUMBER = "number";
  public static final String DATA   = "data";

  private static final ObjectMapper mapper = SystemMapper.getMapper();

  private final MetricRegistry metricRegistry        = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Timer          createTimer           = metricRegistry.timer(name(Accounts.class, "create"          ));
  private final Timer          updateTimer           = metricRegistry.timer(name(Accounts.class, "update"          ));
  private final Timer          getByNumberTimer      = metricRegistry.timer(name(Accounts.class, "getByNumber"     ));
  private final Timer          getByUuidTimer        = metricRegistry.timer(name(Accounts.class, "getByUuid"       ));
  private final Timer          getAllFromTimer       = metricRegistry.timer(name(Accounts.class, "getAllFrom"      ));
  private final Timer          getAllFromOffsetTimer = metricRegistry.timer(name(Accounts.class, "getAllFromOffset"));
  private final Timer          deleteTimer           = metricRegistry.timer(name(Accounts.class, "delete"          ));
  private final Timer          vacuumTimer           = metricRegistry.timer(name(Accounts.class, "vacuum"          ));

  private final FaultTolerantDatabase database;

  public Accounts(FaultTolerantDatabase database) {
    this.database = database;
    this.database.getDatabase().registerRowMapper(new AccountRowMapper());
  }

  @Override
  public boolean create(Account account) {
    return database.with(jdbi -> jdbi.inTransaction(TransactionIsolationLevel.SERIALIZABLE, handle -> {
      try (Timer.Context ignored = createTimer.time()) {
        // Login by email. So check for conflicts on UUID rather than phone number. And don't write any phone number
        UUID uuid = handle.createQuery("INSERT INTO accounts (" + NUMBER + ", " + UID + ", " + DATA + ") VALUES (:number, :uuid, CAST(:data AS json)) ON CONFLICT(uuid) DO UPDATE SET data = EXCLUDED.data RETURNING uuid")
                          .bind("number", "")
                          .bind("uuid", account.getUuid())
                          .bind("data", mapper.writeValueAsString(account))
                          .mapTo(UUID.class)
                          .findOnly();

        boolean isNew = uuid.equals(account.getUuid());
        account.setUuid(uuid);
        return isNew;
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
    }));
  }

  @Override
  public void update(Account account) {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = updateTimer.time()) {
        handle.createUpdate("UPDATE accounts SET " + DATA + " = CAST(:data AS json) WHERE " + UID + " = :uuid")
              .bind("uuid", account.getUuid())
              .bind("data", mapper.writeValueAsString(account))
              .execute();
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
    }));
  }

  @Override
  public Optional<Account> get(String number) {
    // Login by email.
    throw new UnsupportedOperationException("We do not support phone number searches");
  }

  @Override
  public Optional<Account> get(UUID uuid) {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getByUuidTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts WHERE " + UID + " = :uuid")
                     .bind("uuid", uuid)
                     .mapTo(Account.class)
                     .findFirst();
      }
    }));
  }

  public List<Account> getAllFrom(UUID from, int length) {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getAllFromOffsetTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts WHERE " + UID + " > :from ORDER BY " + UID + " LIMIT :limit")
                     .bind("from", from)
                     .bind("limit", length)
                     .mapTo(Account.class)
                     .list();
      }
    }));
  }

  public List<Account> getAllFrom(int length) {
    return database.with(jdbi -> jdbi.withHandle(handle -> {
      try (Timer.Context ignored = getAllFromTimer.time()) {
        return handle.createQuery("SELECT * FROM accounts ORDER BY " + UID + " LIMIT :limit")
                     .bind("limit", length)
                     .mapTo(Account.class)
                     .list();
      }
    }));
  }

  @Override
  public void delete(final UUID uuid) {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = deleteTimer.time()) {
        handle.createUpdate("DELETE FROM accounts WHERE " + UID + " = :uuid")
                .bind("uuid", uuid)
                .execute();
      }
    }));
  }

  public void vacuum() {
    database.use(jdbi -> jdbi.useHandle(handle -> {
      try (Timer.Context ignored = vacuumTimer.time()) {
        handle.execute("VACUUM accounts");
      }
    }));
  }

}
