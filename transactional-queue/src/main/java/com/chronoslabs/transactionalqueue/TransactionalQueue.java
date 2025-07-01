package com.chronoslabs.transactionalqueue;

import static java.util.Objects.requireNonNull;

import com.github.sviperll.result4j.Result;

public class TransactionalQueue<
    P, E extends TransactionalQueueElement<P>, I extends TransactionalQueueElementToEnqueue<P, E>> {
  private final Enqueuer<P, E, I> enqueuer;
  private final Dispatcher<P, E, I> dispatcher;
  private final Retrier<P, E, I> dispatcherRetrier;

  TransactionalQueue(
      Enqueuer<P, E, I> enqueuer,
      Dispatcher<P, E, I> dispatcher,
      Retrier<P, E, I> dispatcherRetrier) {
    this.enqueuer = requireNonNull(enqueuer, "TransactionalQueue.enqueuer");
    this.dispatcher = requireNonNull(dispatcher, "TransactionalQueue.dispatcher");
    this.dispatcherRetrier =
        requireNonNull(dispatcherRetrier, "TransactionalQueue.dispatcherRetrier");
  }

  public Result<E, TransactionalQueueError<I>> enqueue(I elementToEnqueue) {
    return enqueuer.enqueue(elementToEnqueue);
  }

  public void dispatch(E element) {
    dispatcher.dispatch(element);
  }

  public void retryDispatch() {
    dispatcherRetrier.retry();
  }
}
