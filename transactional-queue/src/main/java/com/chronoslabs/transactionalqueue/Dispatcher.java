package com.chronoslabs.transactionalqueue;

import static com.chronoslabs.transactionalqueue.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static java.util.Objects.requireNonNull;

import com.github.sviperll.result4j.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Dispatcher<
    P, E extends TransactionalQueueElement<P>, I extends TransactionalQueueElementToEnqueue<P, E>> {
  private static final Logger logger = LoggerFactory.getLogger(Dispatcher.class);

  private final String queueName;
  private final Clock clock;
  private final MetricHandler metricHandler;
  private final TransactionCreator transactionCreator;
  private final TransactionalQueueElementPayloadConsumer<E> queueElementPayloadConsumer;
  private final TransactionalQueueElementRepository<P, E, I> queueRepository;
  private final TransactionalQueueDeadLetterRepository<E> queueDeadLetterRepository;
  private final TransactionalQueueConfigurationProperties configurationProperties;

  @SuppressWarnings("java:S107")
  Dispatcher(
      String queueName,
      Clock clock,
      MetricHandler metricHandler,
      TransactionCreator transactionCreator,
      TransactionalQueueElementPayloadConsumer<E> queueElementPayloadConsumer,
      TransactionalQueueElementRepository<P, E, I> queueRepository,
      TransactionalQueueDeadLetterRepository<E> queueDeadLetterRepository,
      TransactionalQueueConfigurationProperties configurationProperties) {
    this.queueName = requireNonNull(queueName, "Dispatcher.queueName");
    this.clock = requireNonNull(clock, "Dispatcher.clock");
    this.metricHandler = requireNonNull(metricHandler, "Dispatcher.metricHandler");
    this.transactionCreator = requireNonNull(transactionCreator, "Dispatcher.transactionCreator");
    this.queueElementPayloadConsumer =
        requireNonNull(queueElementPayloadConsumer, "Dispatcher.queueElementPayloadConsumer");
    this.queueRepository = requireNonNull(queueRepository, "Dispatcher.queueRepository");
    this.queueDeadLetterRepository =
        requireNonNull(queueDeadLetterRepository, "Dispatcher.queueDeadLetterRepository");
    this.configurationProperties =
        requireNonNull(configurationProperties, "Dispatcher.configurationProperties");
    configurationProperties.validate(queueName);
  }

  void dispatch(E element) {
    dispatchInternal(element)
        .peekSuccess(this::registerDispatchSuccessMetric)
        .peekError(error -> error.logError(logger))
        .peekError(metricHandler::registerQueueError)
        .peekError(ignored -> handleDispatchError(element));
  }

  Result<E, TransactionalQueueError<E>> dispatchInternal(E element) {
    try {
      return openTransaction(element)
          .flatMap(transaction -> dispatchInTransaction(element, transaction));
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<E>builder(queueName)
              .withCause(exception)
              .withElement(element)
              .withMessage("An unexpected error occurred during dispatching the element.")
              .withType("dispatcher-unexpected-error")
              .build());
    }
  }

  private Result<E, TransactionalQueueError<E>> dispatchInTransaction(
      E element, OpenedTransaction transaction) {
    return tryDispatchInTransactionOrReturnError(element)
        .flatMap(ignored -> commitTransaction(element, transaction))
        .peekError(ignored -> transaction.rollback());
  }

  private Result<E, TransactionalQueueError<E>> tryDispatchInTransactionOrReturnError(E element) {
    try {
      return queueRepository
          .delete(element)
          .flatMap(ignored -> queueElementPayloadConsumer.consumeElementPayload(element));
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<E>builder(queueName)
              .withCause(exception)
              .withElement(element)
              .withMessage(
                  "An unexpected error occurred while consuming the element payload. Cause: "
                      + exception.getMessage())
              .withType("consume-element-payload-unexpected-error")
              .build());
    }
  }

  private void registerDispatchSuccessMetric(E element) {
    var dispatchDuration = Duration.between(element.createdAt(), Instant.now(clock));
    metricHandler.registerDispatchSuccess(dispatchDuration, element.dispatchCount());
  }

  private void handleDispatchError(E element) {
    if (canBeDispatchedAgain(element)) {
      return;
    }
    moveToDeadLetterQueue(element);
  }

  private boolean canBeDispatchedAgain(E element) {
    return element.dispatchCount() < configurationProperties.maxDispatchCount();
  }

  private void moveToDeadLetterQueue(E element) {
    openTransaction(element)
        .flatMap(transaction -> moveToDeadLetterQueue(element, transaction))
        .peekError(
            error ->
                logger
                    .atError()
                    .addArgument(element)
                    .addArgument(error)
                    .log("Failed to move queue element {} to dead letter queue. {}"))
        .peekError(
            error ->
                metricHandler.registerError(error.cause(), "moved-to-dead-letter-queue-error"));
  }

  private Result<E, TransactionalQueueError<E>> moveToDeadLetterQueue(
      E element, OpenedTransaction transaction) {
    return tryMoveToDeadLetterQueueOrReturnError(element)
        .flatMap(ignored -> commitTransaction(element, transaction))
        .peekSuccess(ignored -> registerMovedToDeadLetterQueueSuccessMetric(element))
        .peekError(ignored -> transaction.rollback());
  }

  private Result<E, TransactionalQueueError<E>> tryMoveToDeadLetterQueueOrReturnError(E element) {
    try {
      return queueRepository
          .delete(element)
          .flatMap(ignored -> queueDeadLetterRepository.insert(element))
          .flatMap(ignored -> queueElementPayloadConsumer.consumeElementPayloadFallback(element));
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<E>builder(queueName)
              .withCause(exception)
              .withElement(element)
              .withMessage(
                  "An unexpected error occurred while fallback consume of the element payload."
                      + " Cause: "
                      + exception.getMessage())
              .withType("fallback-consume-element-payload-unexpected-error")
              .build());
    }
  }

  private void registerMovedToDeadLetterQueueSuccessMetric(E element) {
    metricHandler.registerMoveToDeadLetterQueue(element.dispatchCount());
  }

  private Result<OpenedTransaction, TransactionalQueueError<E>> openTransaction(E element) {
    return transactionCreator
        .openTransaction(PROPAGATION_REQUIRES_NEW, queueName)
        .mapError(error -> error.withAnotherElement(element));
  }

  private Result<E, TransactionalQueueError<E>> commitTransaction(
      E element, OpenedTransaction transaction) {
    return transaction.commit(element).mapError(error -> error.withAnotherElement(element));
  }
}
