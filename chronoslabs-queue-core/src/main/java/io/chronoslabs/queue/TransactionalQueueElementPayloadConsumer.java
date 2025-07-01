package io.chronoslabs.queue;

import com.github.sviperll.result4j.Result;

@FunctionalInterface
public interface TransactionalQueueElementPayloadConsumer<E extends TransactionalQueueElement<?>> {

  /**
   * Consumes the payload of the specified queue element. This method is executed within a new
   * transaction and is the primary mechanism for processing the element's payload.
   *
   * @param element the queue element whose payload is to be consumed.
   * @return a {@link Result} representing the outcome of the payload consumption. Returns a success
   *     result if the payload was consumed successfully, or an error result if the consumption
   *     failed.
   */
  Result<E, TransactionalQueueError<E>> consumeElementPayload(E element);

  /**
   * Fallback method for consuming the payload of the specified queue element. This method is
   * invoked only after the primary consumption method {@link #consumeElementPayload(E)} has failed
   * the maximum allowed number of attempts, and the queue element is being moved to the Dead Letter
   * Queue. It is executed within a new transaction, separate from the one used for the primary
   * consumption.
   *
   * <p>By default, this method does not perform any specific action and simply returns a success
   * result with the provided element.
   *
   * @param element the queue element whose payload is to be consumed as part of the fallback
   *     mechanism.
   * @return a {@link Result} representing the outcome of the fallback payload consumption. Returns
   *     a success result if the fallback consumption was successful, or an error result if it
   *     failed.
   */
  default Result<E, TransactionalQueueError<E>> consumeElementPayloadFallback(E element) {
    return Result.success(element);
  }
}
