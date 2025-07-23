package io.chronoslabs.queue

import java.time.Instant

record TestTransactionalQueueElement(
    long id, TestPayload payload, Instant createdAt, Instant nextDispatchAfter, int dispatchCount)
    implements TransactionalQueueElement<TestPayload> {

    Builder toBuilder() {
        return new Builder()
            .withId(id)
            .withPayload(payload)
            .withCreatedAt(createdAt)
            .withNextDispatchAfter(nextDispatchAfter)
            .withDispatchCount(dispatchCount)
    }

    static Builder builder() {
        return new Builder()
    }

    static class Builder implements TransactionalQueueElementBuilder<TestPayload, TestTransactionalQueueElement> {
        private long id
        private TestPayload payload
        private Instant createdAt
        private Instant nextDispatchAfter
        private int dispatchCount

        private Builder() {}

        Builder withId(long id) {
            this.id = id
            return this
        }

        Builder withPayload(TestPayload payload) {
            this.payload = payload
            return this
        }

        Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt
            return this
        }

        Builder withNextDispatchAfter(Instant nextDispatchAfter) {
            this.nextDispatchAfter = nextDispatchAfter
            return this
        }

        Builder withDispatchCount(int dispatchCount) {
            this.dispatchCount = dispatchCount
            return this
        }

        TestTransactionalQueueElement build() {
            return new TestTransactionalQueueElement(
                id, payload, createdAt, nextDispatchAfter, dispatchCount)
        }
    }
}
