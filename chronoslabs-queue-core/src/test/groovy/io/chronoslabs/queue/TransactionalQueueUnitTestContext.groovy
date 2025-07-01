package io.chronoslabs.queue

import io.micrometer.core.instrument.simple.SimpleMeterRegistry

import java.time.Instant
import java.time.temporal.TemporalAmount

class TransactionalQueueUnitTestContext {
    final String queueName
    final UpdatableFixedClock mutableClock
    final SimpleMeterRegistry meterRegistry
    final FakeTransactionCreator transactionCreator = new FakeTransactionCreator()
    final FakeTransactionalQueueApplicationEventPublisher applicationEventPublisher = new FakeTransactionalQueueApplicationEventPublisher()
    final FakeTransactionalQueueElementPayloadConsumer queuePayloadConsumer = new FakeTransactionalQueueElementPayloadConsumer()
    final TestTransactionalQueueElementRepository repository
    final TestTransactionalQueueDeadLetterRepository deadLetterRepository = new TestTransactionalQueueDeadLetterRepository()
    final TransactionalQueueConfigurationProperties configurationProperties
    final TransactionalQueue<TestPayload, TestTransactionalQueueElement, TestTransactionalQueueElementToEnqueue> transactionalQueue

    TransactionalQueueUnitTestContext(String queueName, UpdatableFixedClock mutableClock,
                                      SimpleMeterRegistry meterRegistry,
                                      TransactionalQueueConfigurationProperties configurationProperties) {
        this.queueName = queueName
        this.mutableClock = mutableClock
        this.meterRegistry = meterRegistry
        this.repository = new TestTransactionalQueueElementRepository(queueName)
        this.configurationProperties = configurationProperties

        this.transactionalQueue = TransactionalQueueFactory.<TestPayload, TestTransactionalQueueElement, TestTransactionalQueueElementToEnqueue> factory()
            .withQueueName(queueName)
            .withClock(mutableClock)
            .withMeterRegistry(meterRegistry)
            .withTransactionCreator(transactionCreator)
            .withQueuePayloadConsumer(queuePayloadConsumer)
            .withQueueRepository(repository)
            .withQueueDeadLetterRepository(deadLetterRepository)
            .withApplicationEventPublisher(applicationEventPublisher)
            .withConfigurationProperties(configurationProperties)
            .create()
    }

    Instant nowIs(String now) {
        mutableClock.nowIs(now)
        return mutableClock.instant()
    }

    Instant now() {
        return mutableClock.instant()
    }

    Instant tick(TemporalAmount amountToAdd) {
        Instant newTime = now() + amountToAdd
        mutableClock.nowIs(newTime)
        return newTime
    }

    TransactionalQueueUnitTestContextAssert assertThat() {
        return TransactionalQueueUnitTestContextAssert.assertThat(this)
    }

    void reset() {
        applicationEventPublisher.reset()
        deadLetterRepository.reset()
        meterRegistry.clear()
        mutableClock.reset()
        queuePayloadConsumer.reset()
        repository.reset()
    }
}
