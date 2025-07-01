package io.chronoslabs.queue;

import static io.chronoslabs.queue.TransactionDefinition.PROPAGATION_MANDATORY;
import static java.util.Objects.requireNonNull;

import com.github.sviperll.result4j.Result;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Enqueuer<
    P, E extends TransactionalQueueElement<P>, I extends TransactionalQueueElementToEnqueue<P, E>> {
  private static final Logger logger = LoggerFactory.getLogger(Enqueuer.class);

  private final String queueName;
  private final Clock clock;
  private final MetricHandler metricHandler;
  private final TransactionCreator transactionCreator;
  private final TransactionalQueueElementRepository<P, E, I> queueRepository;

  Enqueuer(
      String queueName,
      Clock clock,
      MetricHandler metricHandler,
      TransactionCreator transactionCreator,
      TransactionalQueueElementRepository<P, E, I> queueRepository) {
    this.queueName = requireNonNull(queueName, "Enqueuer.queueName");
    this.clock = requireNonNull(clock, "Enqueuer.clock");
    this.metricHandler = requireNonNull(metricHandler, "Enqueuer.metricHandler");
    this.transactionCreator = requireNonNull(transactionCreator, "Enqueuer.transactionCreator");
    this.queueRepository = requireNonNull(queueRepository, "Enqueuer.queueRepository");
  }

  public Result<E, TransactionalQueueError<I>> enqueue(I elementToEnqueue) {
    return enqueueInternal(elementToEnqueue)
        .peekError(error -> error.logError(logger))
        .peekError(metricHandler::registerQueueError);
  }

  private Result<E, TransactionalQueueError<I>> enqueueInternal(I elementToEnqueue) {
    try {
      var now = Instant.now(clock);
      var nextDispatchAfter = now.plus(elementToEnqueue.dispatchDelay());

      return ensureTransactionIsAlreadyOpened(elementToEnqueue)
          .flatMap(ignored -> queueRepository.insert(elementToEnqueue, now, nextDispatchAfter));
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<I>builder(queueName)
              .withCause(exception)
              .withElement(elementToEnqueue)
              .withMessage("An unexpected error occurred during adding the element to the queue.")
              .withType("enqueue-unexpected-error")
              .build());
    }
  }

  private Result<OpenedTransaction, TransactionalQueueError<I>> ensureTransactionIsAlreadyOpened(
      I elementToEnqueue) {
    return transactionCreator
        .openTransaction(PROPAGATION_MANDATORY, queueName)
        .mapError(error -> error.withAnotherElement(elementToEnqueue));
  }
}
