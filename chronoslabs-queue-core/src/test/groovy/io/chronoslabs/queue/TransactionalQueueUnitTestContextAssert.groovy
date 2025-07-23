package io.chronoslabs.queue

import org.assertj.core.api.AbstractAssert

class TransactionalQueueUnitTestContextAssert extends AbstractAssert<TransactionalQueueUnitTestContextAssert, TransactionalQueueUnitTestContext> {
    private TransactionalQueueUnitTestContextAssert(TransactionalQueueUnitTestContext actual) {
        super(actual, TransactionalQueueUnitTestContextAssert.class)
    }

    static TransactionalQueueUnitTestContextAssert assertThat(TransactionalQueueUnitTestContext actual) {
        return new TransactionalQueueUnitTestContextAssert(actual)
    }

    TransactionalQueueUnitTestContextAssert hasInRepositoryRowsCountEqualTo(int expected) {
        isNotNull()

        Assertions.assertThat(actual.repository.count())
            .describedAs("Check Transactional Queue Repository has rows count equal to $expected")
            .isEqualTo(expected)

        return this
    }

    TransactionalQueueUnitTestContextAssert hasInRepositoryRowsCountEqualTo(int expected, Closure<Boolean> closure) {
        isNotNull()

        Assertions.assertThat(actual.repository.count(closure))
            .describedAs("Check Transactional Queue Repository has rows count equal to $expected")
            .isEqualTo(expected)

        return this
    }

    TransactionalQueueUnitTestContextAssert hasInDeadLetterRepositoryRowsCountEqualTo(int expected) {
        isNotNull()

        Assertions.assertThat(actual.deadLetterRepository.count())
            .describedAs("Check Transactional Queue Dead Letter Repository has rows count equal to $expected")
            .isEqualTo(expected)

        return this
    }

    TransactionalQueueUnitTestContextAssert hasEmptyRepository() {
        isNotNull()

        Assertions.assertThat(actual.repository.isEmpty())
            .describedAs("Check Transactional Queue Repository is empty")
            .isTrue()

        return this
    }

    TransactionalQueueUnitTestContextAssert doesNotPublishedQueueEvents() {
        isNotNull()

        Assertions.assertThat(actual.applicationEventPublisher.hasNoPublishedEvents())
            .describedAs("Check Transactional Queue does not published queue events")
            .isTrue()

        return this
    }

    TransactionalQueueUnitTestContextAssert hasPublishedQueueApplicationEvents(TestTransactionalQueueElement... expected) {
        isNotNull()

        Assertions.assertThat(actual.applicationEventPublisher.getPublishedEvents())
            .describedAs("Check Transactional Queue has published exact queue application events")
            .containsExactlyInAnyOrder(expected)

        return this
    }

    TransactionalQueueUnitTestContextAssert hasPublishedQueueApplicationEventsCount(int expected) {
        isNotNull()

        Assertions.assertThat(actual.applicationEventPublisher.count())
            .describedAs("Check Transactional Queue Application Events has rows count equal to $expected")
            .isEqualTo(expected)

        return this
    }

    TransactionalQueueUnitTestContextAssert hasConsumedPayloadCount(int expected) {
        isNotNull()

        Assertions.assertThat(actual.queuePayloadConsumer.count())
            .describedAs("Check Transactional Queue Consumed Payloads has count equal to $expected")
            .isEqualTo(expected)

        return this
    }

    TransactionalQueueUnitTestContextAssert hasConsumedPayloads(TestPayload... expected) {
        isNotNull()

        Assertions.assertThat(actual.queuePayloadConsumer.consumedPayloads())
            .describedAs("Check Transactional Queue has Consumed Payloads contains exactly $expected")
            .containsExactlyInAnyOrder(expected)

        return this
    }
}
