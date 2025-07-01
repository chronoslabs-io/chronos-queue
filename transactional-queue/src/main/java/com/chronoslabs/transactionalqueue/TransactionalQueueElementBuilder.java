package com.chronoslabs.transactionalqueue;

import java.time.Instant;

public interface TransactionalQueueElementBuilder<P, E extends TransactionalQueueElement<P>> {
  TransactionalQueueElementBuilder<P, E> withId(long id);

  TransactionalQueueElementBuilder<P, E> withCreatedAt(Instant createdAt);

  TransactionalQueueElementBuilder<P, E> withDispatchCount(int dispatchCount);

  TransactionalQueueElementBuilder<P, E> withNextDispatchAfter(Instant nextDispatchAfter);

  TransactionalQueueElementBuilder<P, E> withPayload(P payload);

  E build();
}
