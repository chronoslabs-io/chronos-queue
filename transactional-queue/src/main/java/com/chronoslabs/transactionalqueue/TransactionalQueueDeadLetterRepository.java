package com.chronoslabs.transactionalqueue;

import com.github.sviperll.result4j.Result;

@FunctionalInterface
public interface TransactionalQueueDeadLetterRepository<E extends TransactionalQueueElement<?>> {
  Result<E, TransactionalQueueError<E>> insert(E element);
}
