package com.chronoslabs.transactionalqueue;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;

public class TransactionalQueueFactory<
    P, E extends TransactionalQueueElement<P>, I extends TransactionalQueueElementToEnqueue<P, E>> {
  private String queueName;
  private Clock clock;
  private MeterRegistry meterRegistry;
  private TransactionCreator transactionCreator;
  private TransactionalQueueApplicationEventPublisher applicationEventPublisher;
  private TransactionalQueueElementPayloadConsumer<E> queuePayloadConsumer;
  private TransactionalQueueElementRepository<P, E, I> queueRepository;
  private TransactionalQueueDeadLetterRepository<E> queueDeadLetterRepository;
  private TransactionalQueueConfigurationProperties configurationProperties;

  private MetricHandler metricHandler;
  private Enqueuer<P, E, I> enqueuer;
  private Retrier<P, E, I> retrier;
  private Dispatcher<P, E, I> dispatcher;

  private TransactionalQueueFactory() {}

  public static <
          P,
          E extends TransactionalQueueElement<P>,
          I extends TransactionalQueueElementToEnqueue<P, E>>
      TransactionalQueueFactory<P, E, I> factory() {
    return new TransactionalQueueFactory<>();
  }

  public TransactionalQueueFactory<P, E, I> withQueueName(String queueName) {
    this.queueName = queueName;
    creatMetricHandler();
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withMeterRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    creatMetricHandler();
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withTransactionCreator(
      TransactionCreator transactionCreator) {
    this.transactionCreator = transactionCreator;
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withApplicationEventPublisher(
      TransactionalQueueApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withQueuePayloadConsumer(
      TransactionalQueueElementPayloadConsumer<E> queuePayloadConsumer) {
    this.queuePayloadConsumer = queuePayloadConsumer;
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withQueueRepository(
      TransactionalQueueElementRepository<P, E, I> queueRepository) {
    this.queueRepository = queueRepository;
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withQueueDeadLetterRepository(
      TransactionalQueueDeadLetterRepository<E> queueDeadLetterRepository) {
    this.queueDeadLetterRepository = queueDeadLetterRepository;
    return this;
  }

  public TransactionalQueueFactory<P, E, I> withConfigurationProperties(
      TransactionalQueueConfigurationProperties configurationProperties) {
    this.configurationProperties = configurationProperties;
    return this;
  }

  private void creatMetricHandler() {
    if (meterRegistry == null || queueName == null) {
      return;
    }
    metricHandler = new MetricHandler(meterRegistry, queueName);
  }

  public TransactionalQueue<P, E, I> create() {
    configurationProperties.validate(queueName);
    createEnqueuer();
    createDispatcher();
    createRetrier();
    return new TransactionalQueue<>(enqueuer, dispatcher, retrier);
  }

  public Enqueuer<P, E, I> createEnqueuer() {
    if (this.enqueuer == null) {
      this.enqueuer =
          new Enqueuer<>(queueName, clock, metricHandler, transactionCreator, queueRepository);
    }
    return this.enqueuer;
  }

  @SuppressWarnings("UnusedReturnValue")
  private Retrier<P, E, I> createRetrier() {
    if (this.retrier == null) {
      this.retrier =
          new Retrier<>(
              queueName,
              clock,
              metricHandler,
              transactionCreator,
              applicationEventPublisher,
              queueRepository,
              configurationProperties);
    }
    return this.retrier;
  }

  @SuppressWarnings("UnusedReturnValue")
  private Dispatcher<P, E, I> createDispatcher() {
    if (this.dispatcher == null) {
      this.dispatcher =
          new Dispatcher<>(
              queueName,
              clock,
              metricHandler,
              transactionCreator,
              queuePayloadConsumer,
              queueRepository,
              queueDeadLetterRepository,
              configurationProperties);
    }
    return this.dispatcher;
  }
}
