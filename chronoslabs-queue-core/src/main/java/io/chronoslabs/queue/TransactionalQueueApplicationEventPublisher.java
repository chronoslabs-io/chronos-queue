package io.chronoslabs.queue;

import com.github.sviperll.result4j.Result;

@FunctionalInterface
public interface TransactionalQueueApplicationEventPublisher {
  <E extends TransactionalQueueElement<?>>
      Result<E, TransactionalQueueError<E>> publishElementAsApplicationEvent(
          E element, String queueName);
}
