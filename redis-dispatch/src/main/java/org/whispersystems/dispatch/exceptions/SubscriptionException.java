package org.whispersystems.dispatch.exceptions;

public class SubscriptionException extends RuntimeException {
  public SubscriptionException(String message) {
    super(message);
  }

  public SubscriptionException(Throwable throwable) {
    super(throwable);
  }
}
