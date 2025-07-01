package com.chronoslabs.transactionalqueue;

import java.time.Instant;

public interface TransactionalQueueElement<P> {
  long id();

  Instant createdAt();

  Instant nextDispatchAfter();

  int dispatchCount();

  P payload();
}
