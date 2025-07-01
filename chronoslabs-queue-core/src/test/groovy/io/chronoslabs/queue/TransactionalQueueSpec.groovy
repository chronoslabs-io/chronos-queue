package io.chronoslabs.queue

import com.github.sviperll.result4j.Result
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import static io.chronoslabs.queue.TestTransactionalQueueElementToEnqueue.aTestTransactionalQueueElementToEnqueue

class TransactionalQueueSpec extends Specification {
    String queueName = "TestQueue"
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()
    UpdatableFixedClock mutableClock = UpdatableFixedClock.defaultUpdatableFixedClock()
    TransactionalQueueConfigurationProperties configurationProperties = TransactionalQueueConfigurationProperties.defaultConfiguration()
    TransactionalQueueUnitTestContext testContext = new TransactionalQueueUnitTestContext(queueName, mutableClock, meterRegistry, configurationProperties)
    TransactionalQueue<TestPayload, TestTransactionalQueueElement, TestTransactionalQueueElementToEnqueue> transactionalQueue = testContext.transactionalQueue

    def "should return success with TestTransactionalQueueElement when enqueue is called"() {
        given: "the current time is set"
            Instant now = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        when: "I enqueue the queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> enqueueResult = transactionalQueue.enqueue(elementToEnqueue)

        then: "the enqueue result is success and contains expected values"
            Assertions.assertThat(enqueueResult)
                .isNotNull()
                .isSuccess()
                .hasSuccessValueThat()
                .isNotNull()
                .asInstanceOf(TransactionalQueueInstanceOfAssertFactory.transactionalQueueElement(TestPayload.class))
                .hasId()
                .hasPayload(elementToEnqueue.payload())
                .hasCreatedAt(now)
                .hasNextDispatchAfter(now)
                .hasDispatchCount(0)

        and: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(1)
                .doesNotPublishedQueueEvents()
    }

    def "should return failure with TestTransactionalQueueElementToEnqueue when enqueue failed on TransactionalQueueElementRepository.insert method"() {
        given: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        and: "an IllegalStateException is thrown when inserting element into transactional queue"
            IllegalStateException insertException = new IllegalStateException("An unexpected error occurred during insert element into transactional queue.")

        and: "the transactional queue repository will return error"
            testContext.repository.willReturnError("insert",
                TransactionalQueueError.<TestTransactionalQueueElementToEnqueue> builder(queueName)
                    .withCause(insertException)
                    .withElement(elementToEnqueue)
                    .withMessage("An unexpected error occurred during insert element into transactional queue. Error: ${insertException.getMessage()}")
                    .withType("insert-error")
                    .build())

        when: "I enqueue the queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> enqueueResult = transactionalQueue.enqueue(elementToEnqueue)

        then: "the enqueue result is error and contains expected values"
            Assertions.assertThat(enqueueResult)
                .isNotNull()
                .isError()
                .hasErrorThat()
                .isNotNull()
                .asInstanceOf(TransactionalQueueInstanceOfAssertFactory.transactionalQueueError(TestTransactionalQueueElementToEnqueue.class))
                .hasCauseThat {
                    it
                        .isExactlyInstanceOf(IllegalStateException)
                        .hasMessage("An unexpected error occurred during insert element into transactional queue.")
                        .hasNoCause()
                }
                .hasElement(elementToEnqueue)
                .hasMessage("An unexpected error occurred during insert element into transactional queue. Error: An unexpected error occurred during insert element into transactional queue.")
                .hasName(queueName)
                .hasType("insert-error")

        and: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasEmptyRepository()
                .doesNotPublishedQueueEvents()

        and: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.IllegalStateException', " +
                "error-type='insert-error', " +
                "queue-name='TestQueue'" +
                "]; count=1.0"
    }

    def "should dispatch again all elements from Transactional Queue when their next_dispatch_after is less than current time"() {
        given: "the initial time is set"
            Instant initialTime = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "the initial next dispatch time is known"
            Instant initialNextDispatchAt = initialTime + configurationProperties.lockTimeout()

        and: "in database there are 15 elements with nextDispatchAfter in the past"
            Instant elementsNextDispatchAtPast = initialTime - configurationProperties.lockTimeout()
            Instant elementsCreatedAtPast = elementsNextDispatchAtPast - configurationProperties.lockTimeout()
            addTestTransactionalQueueElementsToDb(elementsCreatedAtPast, elementsNextDispatchAtPast, 15)

        and: "in database there are 35 elements with nextDispatchAfter in the future"
            Instant elementsNextDispatchInTheFuture = initialTime + configurationProperties.lockTimeout().multipliedBy(2)
            addTestTransactionalQueueElementsToDb(initialTime, elementsNextDispatchInTheFuture, 35)

        and: "the queue table contains 50 rows"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == elementsNextDispatchAtPast
                })
                .hasInRepositoryRowsCountEqualTo(35, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == initialTime &&
                        element.nextDispatchAfter() == elementsNextDispatchInTheFuture
                })
                .hasInRepositoryRowsCountEqualTo(50)

        when: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(configurationProperties.retryDispatchBatchSize(), { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 1 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == initialNextDispatchAt
                })
                .hasInRepositoryRowsCountEqualTo(50 - configurationProperties.retryDispatchBatchSize(), { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0
                })
                .hasPublishedQueueApplicationEventsCount(configurationProperties.retryDispatchBatchSize())

        when: "one second has passed"
            Instant oneSecondAfterInitialTime = testContext.tick(Duration.ofSeconds(1))

        and: "the current next dispatch time is known"
            Instant oneSecondAfterInitialTimeNextDispatchAt = oneSecondAfterInitialTime + configurationProperties.lockTimeout()

        and: "I clear the application events"
            testContext.applicationEventPublisher.reset()

        and: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15 - configurationProperties.retryDispatchBatchSize(), { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 1 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == oneSecondAfterInitialTimeNextDispatchAt
                })
                .hasInRepositoryRowsCountEqualTo(35, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0
                })
                .hasPublishedQueueApplicationEventsCount(15 - configurationProperties.retryDispatchBatchSize())

        when: "one second has passed"
            Instant twoSecondsAfterInitialTime = testContext.tick(Duration.ofSeconds(1))

        and: "the current next dispatch time is known"
            Instant twoSecondsAfterInitialTimeNextDispatchAt = twoSecondsAfterInitialTime + configurationProperties.lockTimeout()

        and: "I clear the application events"
            testContext.applicationEventPublisher.reset()

        and: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(0, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 1 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == twoSecondsAfterInitialTimeNextDispatchAt
                })
                .hasInRepositoryRowsCountEqualTo(35, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0
                })
                .doesNotPublishedQueueEvents()
    }

    def "should register failure metric when retry dispatch failed on opening database transaction"() {
        given: "the initial time is set"
            Instant initialTime = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "in database there are 15 elements with nextDispatchAfter in the past"
            Instant elementsNextDispatchAtPast = initialTime - configurationProperties.lockTimeout()
            Instant elementsCreatedAtPast = elementsNextDispatchAtPast - configurationProperties.lockTimeout()
            addTestTransactionalQueueElementsToDb(elementsCreatedAtPast, elementsNextDispatchAtPast, 15)

        and: "the queue table contains 15 rows"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15)

        and: "the transaction creator will return error on opening transaction"
            IllegalStateException exception = new IllegalStateException(
                "Failed to open transaction with propagation behaviour %s for %s."
                    .formatted("PROPAGATION_REQUIRES_NEW", queueName))
            testContext.transactionCreator.willReturnError("openTransaction",
                TransactionalQueueError.<String> builder(queueName)
                    .withCause(exception)
                    .withElement(queueName)
                    .withMessage(exception.message)
                    .withType("open-transaction-error")
                    .build()
            )

        when: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == elementsNextDispatchAtPast
                })
                .doesNotPublishedQueueEvents()

        and: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.IllegalStateException', " +
                "error-type='open-transaction-error', " +
                "queue-name='TestQueue'" +
                "]; count=1.0"
    }

    def "should register failure metric when retry dispatch failed on opened transaction commit"() {
        given: "the initial time is set"
            Instant initialTime = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "in database there are 15 elements with nextDispatchAfter in the past"
            Instant elementsNextDispatchAtPast = initialTime - configurationProperties.lockTimeout()
            Instant elementsCreatedAtPast = elementsNextDispatchAtPast - configurationProperties.lockTimeout()
            addTestTransactionalQueueElementsToDb(elementsCreatedAtPast, elementsNextDispatchAtPast, 15)

        and: "the transactional queue repository will return error"
            IllegalStateException exception = new IllegalStateException(
                "Failed to commit ")
            testContext.transactionCreator.willReturnError("commit",
                TransactionalQueueError.<String> builder(queueName)
                    .withCause(exception)
                    .withElement(queueName)
                    .withMessage("Failed to commit transaction PROPAGATION_REQUIRES_NEW for TransactionalQueue.")
                    .withType("commit-transaction-error")
                    .build())

        when: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.IllegalStateException', " +
                "error-type='commit-transaction-error', " +
                "queue-name='TestQueue'" +
                "]; count=1.0"
    }

    def "should register failure metric when retry dispatch failed on TransactionalQueueElementRepository.lockForNextDispatch method"() {
        given: "the initial time is set"
            Instant initialTime = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "in database there are 15 elements with nextDispatchAfter in the past"
            Instant elementsNextDispatchAtPast = initialTime - configurationProperties.lockTimeout()
            Instant elementsCreatedAtPast = elementsNextDispatchAtPast - configurationProperties.lockTimeout()
            addTestTransactionalQueueElementsToDb(elementsCreatedAtPast, elementsNextDispatchAtPast, 15)

        and: "the queue table contains 15 rows"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == elementsNextDispatchAtPast
                })
                .hasInRepositoryRowsCountEqualTo(15)

        and: "the transactional queue repository will return error"
            IllegalStateException exception = new IllegalStateException(
                "Failed to lock transactional queue elements for next dispatch")
            testContext.repository.willReturnError("lockForNextDispatch",
                TransactionalQueueError.<String> builder(queueName)
                    .withCause(exception)
                    .withElement("params")
                    .withMessage(exception.message)
                    .withType("retry-dispatch-lock-error")
                    .build())

        when: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == elementsNextDispatchAtPast
                })
                .doesNotPublishedQueueEvents()

        and: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.IllegalStateException', " +
                "error-type='retry-dispatch-lock-error', " +
                "queue-name='TestQueue'" +
                "]; count=1.0"
    }

    def "should register failure metric when retry dispatch failed on QueueElementPublisher.publishElementAsEvent method"() {
        given: "the initial time is set"
            Instant initialTime = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "the initial next dispatch time is known"
            Instant initialNextDispatchAt = initialTime + configurationProperties.lockTimeout()

        and: "in database there are 15 elements with nextDispatchAfter in the past"
            Instant elementsNextDispatchAtPast = initialTime - configurationProperties.lockTimeout()
            Instant elementsCreatedAtPast = elementsNextDispatchAtPast - configurationProperties.lockTimeout()
            addTestTransactionalQueueElementsToDb(elementsCreatedAtPast, elementsNextDispatchAtPast, 15)

        and: "the queue table contains 15 rows"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(15, { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == elementsNextDispatchAtPast
                })
                .hasInRepositoryRowsCountEqualTo(15)

        and: "the transactional queue queue event publisher will return error"
            TestTransactionalQueueElement elementToPublish = testContext.repository.findById(1L).discardError().get()
            RuntimeException publicationException = new RuntimeException("An unexpected error occurred during publishing element as transactional queue event.")

            testContext.applicationEventPublisher.willReturnError(
                TransactionalQueueError.<TestTransactionalQueueElement> builder(queueName)
                    .withCause(publicationException)
                    .withElement(elementToPublish)
                    .withMessage("Failed to publish element $elementToPublish as application event.")
                    .withType("application-event-publication-error")
                    .build())

        when: "I retry dispatch"
            transactionalQueue.retryDispatch()

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(configurationProperties.retryDispatchBatchSize(), { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 1 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == initialNextDispatchAt
                })
                .hasInRepositoryRowsCountEqualTo(15 - configurationProperties.retryDispatchBatchSize(), { TestTransactionalQueueElement element ->
                    element.dispatchCount() == 0 &&
                        element.createdAt() == elementsCreatedAtPast &&
                        element.nextDispatchAfter() == elementsNextDispatchAtPast
                })
                .hasInRepositoryRowsCountEqualTo(15)
                .doesNotPublishedQueueEvents()

        and: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.RuntimeException', " +
                "error-type='application-event-publication-error', " +
                "queue-name='TestQueue'" +
                "]; count=10.0"
    }

    def "should dispatch transactional queue element"() {
        given: "the current time is set"
            Instant now = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        and: "I insert the transactional queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> elementInserted =
                testContext.repository.insert(elementToEnqueue, now, now + Duration.ofSeconds(1))

        and: "send event will take 100 milliseconds"
            testContext.queuePayloadConsumer.willRunBeforeConsume(() -> testContext.tick(Duration.ofMillis(100)))

        and: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(1)

        and: "a TestTransactionalQueueElement exists"
            TestTransactionalQueueElement element = elementInserted.discardError().get()

        when: "I dispatch the transactional queue element"
            transactionalQueue.dispatch(element)

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(0)
                .hasInDeadLetterRepositoryRowsCountEqualTo(0)
                .hasConsumedPayloadCount(1)
                .hasConsumedPayloads(element.payload())

        and: "the TransactionalQueue.success metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.success(TIMER)[" +
                "dispatch-count='0', " +
                "queue-name='TestQueue']; " +
                "count=1.0, total_time=0.1 seconds, max=0.1 seconds"
    }

    def "should not dispatch when failed on TransactionalQueueElementRepository.delete method"() {
        given: "the current time is set"
            Instant now = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        and: "I insert the transactional queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> elementInserted =
                testContext.repository.insert(elementToEnqueue, now, now + Duration.ofSeconds(1))

        and: "a TestTransactionalQueueElement exists"
            TestTransactionalQueueElement element = elementInserted.discardError().get()

        and: "an IllegalStateException is thrown when inserting element into transactional queue"
            IllegalStateException deleteException = new IllegalStateException("An unexpected error occurred during delete element into transactional queue.")

        and: "I will return error on delete method"
            testContext.repository.willReturnError("delete", TransactionalQueueError.<TestTransactionalQueueElement> builder(queueName)
                .withElement(element)
                .withMessage("An unexpected error occurred during delete transactional queue. Error: " + deleteException.message)
                .withType("delete-element-error")
                .build())

        when: "I dispatch the transactional queue element"
            transactionalQueue.dispatch(element)

        then: "the transactional queue is in proper state"
            assertThatTransactionalQueue()
                .hasInRepositoryRowsCountEqualTo(1)
                .hasInDeadLetterRepositoryRowsCountEqualTo(0)
                .hasConsumedPayloadCount(0)

        and: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-type='delete-element-error', " +
                "queue-name='TestQueue']; " +
                "count=1.0"
    }

    def "should register failure metric when failed on TransactionalQueueElementPayloadConsumer.consumeElementPayload method"() {
        given: "the current time is set"
            Instant now = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        and: "I insert the transactional queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> elementInserted =
                testContext.repository.insert(elementToEnqueue, now, now + Duration.ofSeconds(1))

        and: "a TestTransactionalQueueElement exists"
            TestTransactionalQueueElement element = elementInserted.discardError().get()

        and: "a RuntimeException exists"
            RuntimeException exception = new RuntimeException("An unexpected error occurred during sending a event.")

        and: "a Payload Consumer consumeElementPayload method will return error result "
            testContext.queuePayloadConsumer.consumeElementPayloadWillReturnError(TransactionalQueueError.<TestPayload> builder(queueName)
                .withCause(exception)
                .withElement(element.payload())
                .withMessage("Failed to consume element payload.")
                .withType("payload-consumer-error")
                .build())

        when: "I dispatch the transactional queue element"
            transactionalQueue.dispatch(element)

        then: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.RuntimeException', " +
                "error-type='payload-consumer-error', " +
                "queue-name='TestQueue']; " +
                "count=1.0"
    }

    def "should register failure metric when TransactionalQueueElementPayloadConsumer.consumeElementPayload method throws a RuntimeException"() {
        given: "the current time is set"
            Instant now = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        and: "I insert the transactional queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> elementInserted =
                testContext.repository.insert(elementToEnqueue, now, now + Duration.ofSeconds(1))

        and: "a TestTransactionalQueueElement exists"
            TestTransactionalQueueElement element = elementInserted.discardError().get()

        and: "a Payload Consumer will throw an exception from consumeElementPayload method"
            testContext.queuePayloadConsumer.willRunBeforeConsume{ throw new NullPointerException("An unexpected error occurred during sending a event.") }

        when: "I dispatch the transactional queue element"
            transactionalQueue.dispatch(element)

        then: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.NullPointerException', " +
                "error-type='consume-element-payload-unexpected-error', " +
                "queue-name='TestQueue']; " +
                "count=1.0"
    }

    def "should register failure metric when dispatch failed on opened transaction commit"() {
        given: "the current time is set"
            Instant now = testContext.nowIs("2025-04-25T12:00:00Z")

        and: "a TestTransactionalQueueElementToEnqueue exists"
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()

        and: "I insert the transactional queue element"
            Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> elementInserted =
                testContext.repository.insert(elementToEnqueue, now, now + Duration.ofSeconds(1))

        and: "a TestTransactionalQueueElement exists"
            TestTransactionalQueueElement element = elementInserted.discardError().get()

        and: "an Exception exists"
            IllegalStateException exception = new IllegalStateException("Failed to commit")
            testContext.transactionCreator.willReturnError("commit",
                TransactionalQueueError.<String> builder(queueName)
                    .withCause(exception)
                    .withElement(queueName)
                    .withMessage("Failed to commit transaction PROPAGATION_REQUIRES_NEW for $queueName.")
                    .withType("commit-transaction-error")
                    .build())

        when: "I dispatch the transactional queue element"
            transactionalQueue.dispatch(element)

        then: "the TransactionalQueue.error metric should be reported"
            meterRegistry.getMetersAsString() == "TransactionalQueue.error(COUNTER)[" +
                "error-class='java.lang.IllegalStateException', " +
                "error-type='commit-transaction-error', " +
                "queue-name='TestQueue'" +
                "]; count=1.0"
    }

    def cleanup() {
        testContext.reset()
    }

    protected void addTestTransactionalQueueElementsToDb(Instant createdAt, Instant nextDispatchAfter, int count) {
        for (int i = 0; i < count; i++) {
            TestTransactionalQueueElementToEnqueue elementToEnqueue = aTestTransactionalQueueElementToEnqueue()
            testContext.repository.insert(elementToEnqueue, createdAt, nextDispatchAfter)
        }
    }

    protected TransactionalQueueUnitTestContextAssert assertThatTransactionalQueue() {
        return testContext.assertThat()
    }
}
