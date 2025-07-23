package io.chronoslabs.queue;

import java.time.Instant;

public interface TransactionalQueueElement<P> {
  long id();

  Instant createdAt();

  Instant nextDispatchAfter();

  int dispatchCount();

  P payload();
}
