package io.chronoslabs.queue;

import java.time.Duration;

public interface TransactionalQueueElementToEnqueue<P, E extends TransactionalQueueElement<P>> {

  P payload();

  default Duration dispatchDelay() {
    return Duration.ZERO;
  }

  TransactionalQueueElementBuilder<P, E> toTransactionalQueueElementBuilder();
}
