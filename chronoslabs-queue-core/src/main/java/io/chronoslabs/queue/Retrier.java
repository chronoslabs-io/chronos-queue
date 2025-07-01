package io.chronoslabs.queue;

import static io.chronoslabs.queue.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static java.util.Objects.requireNonNull;

import com.github.sviperll.result4j.Result;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Retrier<
    P, E extends TransactionalQueueElement<P>, I extends TransactionalQueueElementToEnqueue<P, E>> {
  private static final Logger logger = LoggerFactory.getLogger(Retrier.class);

  private final String queueName;
  private final Clock clock;
  private final MetricHandler metricHandler;
  private final TransactionCreator transactionCreator;
  private final TransactionalQueueApplicationEventPublisher applicationEventPublisher;
  private final TransactionalQueueElementRepository<P, E, I> queueRepository;
  private final TransactionalQueueConfigurationProperties configurationProperties;

  Retrier(
      String queueName,
      Clock clock,
      MetricHandler metricHandler,
      TransactionCreator transactionCreator,
      TransactionalQueueApplicationEventPublisher applicationEventPublisher,
      TransactionalQueueElementRepository<P, E, I> queueRepository,
      TransactionalQueueConfigurationProperties configurationProperties) {
    this.queueName = requireNonNull(queueName, "Retrier.queueName");
    this.clock = requireNonNull(clock, "Retrier.clock");
    this.metricHandler = requireNonNull(metricHandler, "Retrier.metricHandler");
    this.transactionCreator = requireNonNull(transactionCreator, "Retrier.transactionCreator");
    this.applicationEventPublisher =
        requireNonNull(applicationEventPublisher, "Retrier.applicationEventPublisher");
    this.queueRepository = requireNonNull(queueRepository, "Retrier.queueRepository");
    this.configurationProperties =
        requireNonNull(configurationProperties, "Retrier.configurationProperties");
    configurationProperties.validate(queueName);
  }

  void retry() {
    findAndLockElementsForRetry()
        .peekSuccess(elements -> elements.forEach(this::publishApplicationEvent))
        .peekError(error -> error.logError(logger))
        .peekError(metricHandler::registerQueueError);
  }

  private Result<Collection<E>, TransactionalQueueError<String>> findAndLockElementsForRetry() {
    try {
      var now = Instant.now(clock);
      var nextDispatchAfter = now.plus(configurationProperties.lockTimeout());
      return openNewTransaction()
          .flatMap(
              transaction ->
                  findAndLockElementsForRetryInTransaction(transaction, now, nextDispatchAfter));
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<String>builder(queueName)
              .withCause(exception)
              .withElement(queueName)
              .withMessage("An unexpected error occurred during lock queue elements for retry.")
              .withType("retrier-unexpected-error-on-find-and-lock")
              .build());
    }
  }

  private Result<Collection<E>, TransactionalQueueError<String>>
      findAndLockElementsForRetryInTransaction(
          OpenedTransaction transaction, Instant now, Instant nextDispatchAfter) {
    return queueRepository
        .lockForNextDispatch(
            configurationProperties.retryDispatchBatchSize(), now, nextDispatchAfter)
        .flatMap(transaction::commit)
        .peekError(ignored -> transaction.rollback());
  }

  private void publishApplicationEvent(E element) {
    try {
      applicationEventPublisher
          .publishElementAsApplicationEvent(element, queueName)
          .peekError(error -> error.logError(logger))
          .peekError(metricHandler::registerQueueError);
    } catch (Exception exception) {
      logger.error("Error while publishing application event.", exception);
      metricHandler.registerError(
          exception, "retrier-unexpected-error-on-publish-application-event");
    }
  }

  private Result<OpenedTransaction, TransactionalQueueError<String>> openNewTransaction() {
    return transactionCreator.openTransaction(PROPAGATION_REQUIRES_NEW, queueName);
  }
}
