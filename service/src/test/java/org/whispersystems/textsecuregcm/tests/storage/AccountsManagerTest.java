package org.whispersystems.textsecuregcm.tests.storage;

import org.junit.Ignore;
import org.junit.Test;
import org.whispersystems.textsecuregcm.experiment.Experiment;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Accounts;
import org.whispersystems.textsecuregcm.storage.AccountsManager;

import java.util.HashSet;
import java.util.Optional;

import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

public class AccountsManagerTest {

  public static final String ACCOUNT_MAP_ALICE = "AccountMap::" + UUID_ALICE_STRING;
  public static final String ACCOUNT_ENTITY_ALICE = "Account3::" + UUID_ALICE_STRING;

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberInCache() {
    ReplicatedJedisPool       cacheClient      = mock(ReplicatedJedisPool.class      );
    Jedis                     jedis            = mock(Jedis.class                    );
    FaultTolerantRedisCluster cacheCluster     = mock(FaultTolerantRedisCluster.class);
    Accounts                  accounts         = mock(Accounts.class                 );

    java.util.UUID uuid = UUID_ALICE;

    when(cacheClient.getReadResource()).thenReturn(jedis);
    when(jedis.get(eq("AccountMap::+14152222222"))).thenReturn(uuid.toString());
    when(jedis.get(eq("Account3::" + uuid.toString()))).thenReturn("{\"number\": \"+14152222222\", \"name\": \"test\"}");

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster, mock(Experiment.class));
    Optional<Account> account         = accountsManager.get("+14152222222");

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), "+14152222222");
    assertEquals(account.get().getProfileName(), "test");

    verify(jedis, times(1)).get(eq("AccountMap::+14152222222"));
    verify(jedis, times(1)).get(eq("Account3::" + uuid.toString()));
    verify(jedis, times(1)).close();
    verifyNoMoreInteractions(jedis);
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidInCache() {
    ReplicatedJedisPool       cacheClient      = mock(ReplicatedJedisPool.class      );
    Jedis                     jedis            = mock(Jedis.class                    );
    FaultTolerantRedisCluster cacheCluster     = mock(FaultTolerantRedisCluster.class);
    Accounts                  accounts         = mock(Accounts.class                 );

    when(cacheClient.getReadResource()).thenReturn(jedis);
    when(jedis.get(eq(ACCOUNT_ENTITY_ALICE))).thenReturn("{\"number\": \""+ UUID_ALICE_STRING +"\", \"name\": \"test\"}");

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster, mock(Experiment.class));
    Optional<Account> account         = accountsManager.get(UUID_ALICE);

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), UUID_ALICE_STRING);
    assertEquals(account.get().getUuid(), UUID_ALICE);
    assertEquals(account.get().getProfileName(), "test");

    verify(jedis, times(1)).get(eq(ACCOUNT_ENTITY_ALICE));
    verify(jedis, times(1)).close();
    verifyNoMoreInteractions(jedis);
    verifyNoMoreInteractions(accounts);
  }

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberNotInCache() {
    ReplicatedJedisPool       cacheClient      = mock(ReplicatedJedisPool.class      );
    Jedis                     jedis            = mock(Jedis.class                    );
    FaultTolerantRedisCluster cacheCluster     = mock(FaultTolerantRedisCluster.class);
    Accounts                  accounts         = mock(Accounts.class                 );
    java.util.UUID            uuid             = UUID_ALICE;
    Account                   account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(cacheClient.getReadResource()).thenReturn(jedis);
    when(cacheClient.getWriteResource()).thenReturn(jedis);
    when(jedis.get(eq("AccountMap::+14152222222"))).thenReturn(null);
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster, mock(Experiment.class));
    Optional<Account> retrieved       = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(jedis, times(1)).get(eq("AccountMap::+14152222222"));
    verify(jedis, times(1)).set(eq("AccountMap::+14152222222"), eq(uuid.toString()));
    verify(jedis, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verify(jedis, times(2)).close();
    verifyNoMoreInteractions(jedis);

    verify(accounts, times(1)).get(eq("+14152222222"));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidNotInCache() {
    ReplicatedJedisPool       cacheClient      = mock(ReplicatedJedisPool.class      );
    Jedis                     jedis            = mock(Jedis.class                    );
    FaultTolerantRedisCluster cacheCluster     = mock(FaultTolerantRedisCluster.class);
    Accounts                  accounts         = mock(Accounts.class                 );
    java.util.UUID            uuid             = UUID_ALICE;
    Account                   account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(cacheClient.getReadResource()).thenReturn(jedis);
    when(cacheClient.getWriteResource()).thenReturn(jedis);
    when(jedis.get(eq("Account3::" + uuid))).thenReturn(null);
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster, mock(Experiment.class));
    Optional<Account> retrieved       = accountsManager.get(uuid);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(jedis, times(1)).get(eq("Account3::" + uuid));
    verify(jedis, times(1)).set(eq("AccountMap::" + uuid), eq(uuid.toString()));
    verify(jedis, times(1)).set(eq("Account3::" + uuid), anyString());
    verify(jedis, times(2)).close();
    verifyNoMoreInteractions(jedis);

    verify(accounts, times(1)).get(eq(uuid));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByNumberBrokenCache() {
    ReplicatedJedisPool       cacheClient      = mock(ReplicatedJedisPool.class      );
    Jedis                     jedis            = mock(Jedis.class                    );
    FaultTolerantRedisCluster cacheCluster     = mock(FaultTolerantRedisCluster.class);
    Accounts                  accounts         = mock(Accounts.class                 );
    java.util.UUID            uuid             = UUID_ALICE;
    Account                   account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(cacheClient.getReadResource()).thenReturn(jedis);
    when(cacheClient.getWriteResource()).thenReturn(jedis);
    when(jedis.get(eq(ACCOUNT_ENTITY_ALICE))).thenReturn(null);
    when(accounts.get(eq(UUID_ALICE))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster, mock(Experiment.class));
    Optional<Account> retrieved       = accountsManager.get(UUID_ALICE);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(jedis, times(1)).get(eq(ACCOUNT_ENTITY_ALICE));
    verify(jedis, times(1)).set(eq(ACCOUNT_MAP_ALICE), eq(UUID_ALICE_STRING));
    verify(jedis, times(1)).set(eq(ACCOUNT_ENTITY_ALICE), anyString());
    verify(jedis, times(2)).close();
    verifyNoMoreInteractions(jedis);

    verify(accounts, times(1)).get(eq(UUID_ALICE));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidBrokenCache() {
    ReplicatedJedisPool       cacheClient      = mock(ReplicatedJedisPool.class      );
    Jedis                     jedis            = mock(Jedis.class                    );
    FaultTolerantRedisCluster cacheCluster     = mock(FaultTolerantRedisCluster.class);
    Accounts                  accounts         = mock(Accounts.class                 );
    java.util.UUID            uuid             = UUID_ALICE;
    Account                   account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(cacheClient.getReadResource()).thenReturn(jedis);
    when(cacheClient.getWriteResource()).thenReturn(jedis);
    when(jedis.get(eq(ACCOUNT_ENTITY_ALICE))).thenThrow(new JedisException("Connection lost!"));
    when(accounts.get(eq(UUID_ALICE))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheClient, cacheCluster, mock(Experiment.class));
    Optional<Account> retrieved       = accountsManager.get(UUID_ALICE);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(jedis, times(1)).get(eq(ACCOUNT_ENTITY_ALICE));
    verify(jedis, times(1)).set(eq(ACCOUNT_MAP_ALICE), eq(UUID_ALICE_STRING));
    verify(jedis, times(1)).set(eq(ACCOUNT_ENTITY_ALICE), anyString());
    verify(jedis, times(2)).close();
    verifyNoMoreInteractions(jedis);

    verify(accounts, times(1)).get(eq(UUID_ALICE));
    verifyNoMoreInteractions(accounts);
  }


}
