package org.whispersystems.textsecuregcm.tests.storage;

import org.junit.Ignore;
import io.lettuce.core.RedisException;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import org.junit.Ignore;
import org.junit.Test;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Accounts;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.tests.util.RedisClusterHelper;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.UUID_ALICE;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.UUID_ALICE_STRING;

public class AccountsManagerTest {

  public static final String ACCOUNT_MAP_ALICE = "AccountMap::" + UUID_ALICE_STRING;
  public static final String ACCOUNT_ENTITY_ALICE = "Account3::" + UUID_ALICE_STRING;

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberInCache() {
    RedisAdvancedClusterCommands<String, String> commands         = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster     = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts         = mock(Accounts.class);

    UUID uuid = UUID_ALICE;

    when(commands.get(eq("AccountMap::"+UUID_ALICE_STRING))).thenReturn(uuid.toString());
    when(commands.get(eq("Account3::" + uuid.toString()))).thenReturn("{\"number\": \"+14152222222\", \"name\": \"test\"}");

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster);
    Optional<Account> account         = accountsManager.get("+14152222222");

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), "+14152222222");
    assertEquals(account.get().getProfileName(), "test");

    verify(commands, times(1)).get(eq("AccountMap::"+UUID_ALICE_STRING));
    verify(commands, times(1)).get(eq("Account3::" + uuid.toString()));
    verifyNoMoreInteractions(commands);
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidInCache() {
    RedisAdvancedClusterCommands<String, String> commands         = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster     = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts         = mock(Accounts.class);

    UUID uuid = UUID_ALICE;

    when(commands.get(eq("Account3::" + uuid.toString()))).thenReturn("{\"number\": \""+UUID_ALICE_STRING+"\", \"name\": \"test\"}");

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster);
    Optional<Account> account         = accountsManager.get(uuid);

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), uuid.toString());
    assertEquals(account.get().getUuid(), uuid);
    assertEquals(account.get().getProfileName(), "test");

    verify(commands, times(1)).get(eq("Account3::" + UUID_ALICE_STRING));
    verifyNoMoreInteractions(commands);
    verifyNoMoreInteractions(accounts);
  }

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberNotInCache() {
    RedisAdvancedClusterCommands<String, String> commands         = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster     = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts         = mock(Accounts.class);
    UUID                                         uuid             = UUID_ALICE;
    Account                                      account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("AccountMap::"+UUID_ALICE_STRING))).thenReturn(null);
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster);
    Optional<Account> retrieved       = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("AccountMap::"+UUID_ALICE_STRING));
    verify(commands, times(1)).set(eq("AccountMap::"+UUID_ALICE_STRING), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq("+14152222222"));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidNotInCache() {
    RedisAdvancedClusterCommands<String, String> commands         = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster     = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts         = mock(Accounts.class);
    UUID                                         uuid             = UUID_ALICE;
    Account                                      account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenReturn(null);
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster);
    Optional<Account> retrieved       = accountsManager.get(uuid);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verify(commands, times(1)).set(eq("AccountMap::" + uuid), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq(uuid));
    verifyNoMoreInteractions(accounts);
  }

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberBrokenCache() {
    RedisAdvancedClusterCommands<String, String> commands         = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster     = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts         = mock(Accounts.class);
    UUID                                         uuid             = UUID_ALICE;
    Account                                      account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("AccountMap::"+UUID_ALICE_STRING))).thenThrow(new RedisException("Connection lost!"));
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster);
    Optional<Account> retrieved       = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("AccountMap::"+UUID_ALICE_STRING));
    verify(commands, times(1)).set(eq("AccountMap::"+UUID_ALICE_STRING), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq(UUID_ALICE));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidBrokenCache() {
    RedisAdvancedClusterCommands<String, String> commands         = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster     = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts         = mock(Accounts.class);
    UUID                                         uuid             = UUID_ALICE;
    Account                                      account          = new Account(uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenThrow(new RedisException("Connection lost!"));
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster);
    Optional<Account> retrieved       = accountsManager.get(uuid);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verify(commands, times(1)).set(eq("AccountMap::" + uuid), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq(UUID_ALICE));
    verifyNoMoreInteractions(accounts);
  }


}
