package org.whispersystems.textsecuregcm.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.dispatch.DispatchChannel;
import org.whispersystems.dispatch.DispatchManager;
import org.whispersystems.dispatch.exceptions.DuplicateSubscriptionException;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;

import io.dropwizard.lifecycle.Managed;
import static org.whispersystems.textsecuregcm.storage.PubSubProtos.PubSubMessage;
import redis.clients.jedis.Jedis;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public class PubSubManager implements Managed {

  private static final String KEEPALIVE_CHANNEL = "KEEPALIVE";

  private final Logger logger = LoggerFactory.getLogger(PubSubManager.class);

  private final DispatchManager     dispatchManager;
  private final ReplicatedJedisPool jedisPool;
  private final RateLimiter connectWebSocketLimiter;

  private boolean subscribed = false;

  public PubSubManager(ReplicatedJedisPool jedisPool, DispatchManager dispatchManager, RateLimiter connectWebSocketLimiter) {
    this.dispatchManager = dispatchManager;
    this.jedisPool       = jedisPool;
    this.connectWebSocketLimiter = connectWebSocketLimiter;
  }

  @Override
  public void start() throws Exception {
    this.dispatchManager.start();

    KeepaliveDispatchChannel keepaliveDispatchChannel = new KeepaliveDispatchChannel();
    this.dispatchManager.subscribe(KEEPALIVE_CHANNEL, keepaliveDispatchChannel);

    synchronized (this) {
      while (!subscribed) wait(0);
    }

    new KeepaliveSender().start();
  }

  @Override
  public void stop() throws Exception {
    dispatchManager.shutdown();
  }

  public void subscribe(PubSubAddress address, DispatchChannel channel) {
    // [Diskuv Change] Denial of service mitigation.
    // See diskuv-changes/2021-03-15-denial-of-service-mitigation.md in Android client.
    String plainAddress = address.serialize();
    try {
      connectWebSocketLimiter.validate(plainAddress);
    } catch (RateLimitExceededException e) {
      // Throwing an error will close the connection. Sadly it won't tell the status to the
      // client so it knows what happened.
      throw new WebApplicationException(e, /* Status 429 */ Response.Status.TOO_MANY_REQUESTS);
    }
    dispatchManager.subscribe(plainAddress, channel);
  }

  public void unsubscribe(PubSubAddress address, DispatchChannel dispatchChannel) {
    dispatchManager.unsubscribe(address.serialize(), dispatchChannel);
  }

  public boolean hasLocalSubscription(PubSubAddress address) {
    return dispatchManager.hasSubscription(address.serialize());
  }

  public boolean publish(PubSubAddress address, PubSubMessage message) {
    return publish(address.serialize().getBytes(), message);
  }

  private boolean publish(byte[] channel, PubSubMessage message) {
    try (Jedis jedis = jedisPool.getWriteResource()) {
      long result = jedis.publish(channel, message.toByteArray());

      if (result < 0) {
        logger.warn("**** Jedis publish result < 0");
      }

      return result > 0;
    }
  }

  private class KeepaliveDispatchChannel implements DispatchChannel {

    @Override
    public void onDispatchMessage(String channel, byte[] message) {
      // Good
    }

    @Override
    public void onDispatchSubscribed(String channel) {
      if (KEEPALIVE_CHANNEL.equals(channel)) {
        synchronized (PubSubManager.this) {
          subscribed = true;
          PubSubManager.this.notifyAll();
        }
      }
    }

    @Override
    public void onDispatchUnsubscribed(String channel) {
      logger.warn("***** KEEPALIVE CHANNEL UNSUBSCRIBED *****");
    }
  }

  private class KeepaliveSender extends Thread {
    @Override
    public void run() {
      while (true) {
        try {
          Thread.sleep(20000);
          publish(KEEPALIVE_CHANNEL.getBytes(), PubSubMessage.newBuilder()
                                                             .setType(PubSubMessage.Type.KEEPALIVE)
                                                             .build());
        } catch (Throwable e) {
          logger.warn("***** KEEPALIVE EXCEPTION ******", e);
        }
      }
    }
  }
}
