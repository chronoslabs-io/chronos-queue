package com.chronoslabs.transactionalqueue;

import com.github.sviperll.result4j.Result;
import java.time.Instant;
import java.util.Collection;

public interface TransactionalQueueElementRepository<
    P, E extends TransactionalQueueElement<P>, I extends TransactionalQueueElementToEnqueue<P, E>> {
  int INITIAL_DISPATCH_COUNT = 0;

  Result<E, TransactionalQueueError<I>> insert(
      I elementToEnqueue, Instant createdAt, Instant nextDispatchAfter);

  Result<E, TransactionalQueueError<Long>> findById(long id);

  Result<Collection<E>, TransactionalQueueError<String>> lockForNextDispatch(
      int batchSize, Instant notDispatchedTill, Instant nextDispatchTime);

  Result<E, TransactionalQueueError<E>> delete(E element);
}
