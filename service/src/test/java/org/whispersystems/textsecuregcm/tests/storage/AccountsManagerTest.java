package org.whispersystems.textsecuregcm.tests.storage;

import org.junit.Ignore;
import io.lettuce.core.RedisException;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import org.junit.Ignore;
import org.junit.Test;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.whispersystems.textsecuregcm.securestorage.SecureStorageClient;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Accounts;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.KeysDynamoDb;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.ProfilesManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
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

public class AccountsManagerTest {

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberInCache() {
    RedisAdvancedClusterCommands<String, String> commands            = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster        = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts            = mock(Accounts.class);
    KeysDynamoDb                                 keysDynamoDb        = mock(KeysDynamoDb.class);
    MessagesManager                              messagesManager     = mock(MessagesManager.class);
    UsernamesManager                             usernamesManager    = mock(UsernamesManager.class);
    ProfilesManager                              profilesManager     = mock(ProfilesManager.class);
    SecureStorageClient                          secureStorageClient = mock(SecureStorageClient.class);

    UUID uuid = UUID_ALICE;

    when(commands.get(eq("AccountMap::"+uuid))).thenReturn(uuid.toString());
    when(commands.get(eq("Account3::" + uuid.toString()))).thenReturn("{\"number\": \"+14152222222\", \"name\": \"test\"}");

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster, keysDynamoDb, messagesManager, usernamesManager, profilesManager, secureStorageClient);
    Optional<Account> account         = accountsManager.get("+14152222222");

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), "+14152222222");
    assertEquals(account.get().getProfileName(), "test");

    verify(commands, times(1)).get(eq("AccountMap::"+uuid));
    verify(commands, times(1)).get(eq("Account3::" + uuid.toString()));
    verifyNoMoreInteractions(commands);
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidInCache() {
    RedisAdvancedClusterCommands<String, String> commands            = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster        = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts            = mock(Accounts.class);
    KeysDynamoDb                                 keysDynamoDb        = mock(KeysDynamoDb.class);
    MessagesManager                              messagesManager     = mock(MessagesManager.class);
    UsernamesManager                             usernamesManager    = mock(UsernamesManager.class);
    ProfilesManager                              profilesManager     = mock(ProfilesManager.class);
    SecureStorageClient                          secureStorageClient = mock(SecureStorageClient.class);

    UUID uuid = UUID_ALICE;

    when(commands.get(eq("Account3::" + uuid.toString()))).thenReturn("{\"number\": \""+uuid+"\", \"name\": \"test\"}");

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster, keysDynamoDb, messagesManager, usernamesManager, profilesManager, secureStorageClient);
    Optional<Account> account         = accountsManager.get(uuid);

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), uuid.toString());
    assertEquals(account.get().getUuid(), uuid);
    assertEquals(account.get().getProfileName(), "test");

    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verifyNoMoreInteractions(commands);
    verifyNoMoreInteractions(accounts);
  }

  @Ignore("Diskuv does not use phone numbers")
  @Test
  public void testGetAccountByNumberNotInCache() {
    RedisAdvancedClusterCommands<String, String> commands            = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster        = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts            = mock(Accounts.class);
    KeysDynamoDb                                 keysDynamoDb        = mock(KeysDynamoDb.class);
    MessagesManager                              messagesManager     = mock(MessagesManager.class);
    UsernamesManager                             usernamesManager    = mock(UsernamesManager.class);
    ProfilesManager                              profilesManager     = mock(ProfilesManager.class);
    SecureStorageClient                          secureStorageClient = mock(SecureStorageClient.class);
    UUID                                         uuid                = UUID_ALICE;
    Account                                      account             = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("AccountMap::"+uuid))).thenReturn(null);
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster, keysDynamoDb, messagesManager, usernamesManager, profilesManager, secureStorageClient);
    Optional<Account> retrieved       = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("AccountMap::"+uuid));
    verify(commands, times(1)).set(eq("AccountMap::"+uuid), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq("+14152222222"));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidNotInCache() {
    RedisAdvancedClusterCommands<String, String> commands            = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster        = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts            = mock(Accounts.class);
    KeysDynamoDb                                 keysDynamoDb        = mock(KeysDynamoDb.class);
    MessagesManager                              messagesManager     = mock(MessagesManager.class);
    UsernamesManager                             usernamesManager    = mock(UsernamesManager.class);
    ProfilesManager                              profilesManager     = mock(ProfilesManager.class);
    SecureStorageClient                          secureStorageClient = mock(SecureStorageClient.class);
    UUID                                         uuid                = UUID.randomUUID();
    Account                                      account             = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenReturn(null);
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster, keysDynamoDb, messagesManager, usernamesManager, profilesManager, secureStorageClient);
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
    RedisAdvancedClusterCommands<String, String> commands            = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster        = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts            = mock(Accounts.class);
    KeysDynamoDb                                 keysDynamoDb        = mock(KeysDynamoDb.class);
    MessagesManager                              messagesManager     = mock(MessagesManager.class);
    UsernamesManager                             usernamesManager    = mock(UsernamesManager.class);
    ProfilesManager                              profilesManager     = mock(ProfilesManager.class);
    SecureStorageClient                          secureStorageClient = mock(SecureStorageClient.class);
    UUID                                         uuid                = UUID.randomUUID();
    Account                                      account             = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("AccountMap::"+uuid))).thenThrow(new RedisException("Connection lost!"));
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster, keysDynamoDb, messagesManager, usernamesManager, profilesManager, secureStorageClient);
    Optional<Account> retrieved       = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("AccountMap::"+uuid));
    verify(commands, times(1)).set(eq("AccountMap::"+uuid), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid.toString()), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq(uuid));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  public void testGetAccountByUuidBrokenCache() {
    RedisAdvancedClusterCommands<String, String> commands            = mock(RedisAdvancedClusterCommands.class);
    FaultTolerantRedisCluster                    cacheCluster        = RedisClusterHelper.buildMockRedisCluster(commands);
    Accounts                                     accounts            = mock(Accounts.class);
    KeysDynamoDb                                 keysDynamoDb        = mock(KeysDynamoDb.class);
    MessagesManager                              messagesManager     = mock(MessagesManager.class);
    UsernamesManager                             usernamesManager    = mock(UsernamesManager.class);
    ProfilesManager                              profilesManager     = mock(ProfilesManager.class);
    SecureStorageClient                          secureStorageClient = mock(SecureStorageClient.class);
    UUID                                         uuid                = UUID.randomUUID();
    Account                                      account             = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenThrow(new RedisException("Connection lost!"));
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    AccountsManager   accountsManager = new AccountsManager(accounts, cacheCluster, keysDynamoDb, messagesManager, usernamesManager, profilesManager, secureStorageClient);
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


}
