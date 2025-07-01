package com.chronoslabs.transactionalqueue

import com.github.sviperll.result4j.Result

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TestTransactionalQueueElementRepository implements TransactionalQueueElementRepository<
    TestPayload, TestTransactionalQueueElement, TestTransactionalQueueElementToEnqueue> {

    private final AtomicLong ID_GENERATOR = new AtomicLong(0)
    private final Map<Long, TestTransactionalQueueElement> elements = new ConcurrentHashMap<>()
    private final Map<String, TransactionalQueueError<?>> errors = [
        "delete"             : null,
        "findById"           : null,
        "insert"             : null,
        "lockForNextDispatch": null,
    ] as Map<String, TransactionalQueueError<?>>

    private final String queueName

    TestTransactionalQueueElementRepository(String queueName) {
        this.queueName = queueName
    }

    @Override
    Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElementToEnqueue>> insert(TestTransactionalQueueElementToEnqueue elementToEnqueue, Instant createdAt, Instant nextDispatchAfter) {
        TransactionalQueueError<TestTransactionalQueueElementToEnqueue> errorResult = errors.get("insert") as TransactionalQueueError<TestTransactionalQueueElementToEnqueue>
        if (errorResult != null) {
            return Result.error(errorResult)
        }

        TestTransactionalQueueElement element = elementToEnqueue
            .toTransactionalQueueElementBuilder()
            .withId(ID_GENERATOR.incrementAndGet())
            .withCreatedAt(createdAt)
            .withDispatchCount(INITIAL_DISPATCH_COUNT)
            .withNextDispatchAfter(nextDispatchAfter)
            .build()
        elements.put(element.id(), element)
        return Result.success(element)
    }

    TestTransactionalQueueElement setElementDispatchCount(TestTransactionalQueueElement element, int maximumNumberOfDispatches) {
        TestTransactionalQueueElement updatedElement = element.toBuilder()
            .withDispatchCount(maximumNumberOfDispatches)
            .build()
        elements.put(element.id(), updatedElement)
        return updatedElement
    }

    @Override
    Result<TestTransactionalQueueElement, TransactionalQueueError<Long>> findById(long id) {
        TransactionalQueueError<Long> errorResult = errors.get("findById") as TransactionalQueueError<Long>
        if (errorResult != null) {
            return Result.error(errorResult)
        }

        TestTransactionalQueueElement element = elements.get(id)
        if (element == null) {
            return Result.error(TransactionalQueueError.<Long> builder(queueName)
                .withElement(id)
                .withMessage("Failed to select webhook outbox element with id=%d.".formatted(id))
                .withType("read-element-error")
                .build())
        }
        return Result.success(element)
    }

    @Override
    Result<TestTransactionalQueueElement, TransactionalQueueError<TestTransactionalQueueElement>> delete(TestTransactionalQueueElement element) {
        TransactionalQueueError<TestTransactionalQueueElement> errorResult = errors.get("delete") as TransactionalQueueError<TestTransactionalQueueElement>
        if (errorResult != null) {
            return Result.error(errorResult)
        }

        TestTransactionalQueueElement removedElement = elements.remove(element.id())
        if (removedElement == null) {
            return Result.error(TransactionalQueueError.<TestTransactionalQueueElement> builder(queueName)
                .withElement(element)
                .withMessage("Element with id=%d does not exist or could not be removed.".formatted(element.id()))
                .withType("delete-element-error")
                .build())
        }

        return Result.success(element)
    }

    @Override
    Result<Collection<TestTransactionalQueueElement>, TransactionalQueueError<String>> lockForNextDispatch(int batchSize, Instant notDispatchedTill, Instant nextDispatchTime) {
        TransactionalQueueError<String> errorResult = errors.get("lockForNextDispatch") as TransactionalQueueError<String>
        if (errorResult != null) {
            return Result.error(errorResult)
        }

        var elementsToDispatch = elements
            .findAll { it.getValue().nextDispatchAfter().isBefore(notDispatchedTill) }
            .take(batchSize)
            .collectEntries { Long key, TestTransactionalQueueElement element ->
                [
                    (key): element.toBuilder()
                        .withDispatchCount(element.dispatchCount() + 1)
                        .withNextDispatchAfter(nextDispatchTime)
                        .build()
                ]
            } as Map<Long, TestTransactionalQueueElement>
        elements.putAll(elementsToDispatch)
        return Result.success(elementsToDispatch.values())
    }

    boolean isEmpty() {
        return elements.isEmpty()
    }

    int count() {
        return elements.size()
    }

    int count(Closure<Boolean> closure) {
        return elements.values().count { TestTransactionalQueueElement element ->
            closure.call(element)
        }
    }

    void willReturnError(String methodName, TransactionalQueueError<?> error) {
        errors.put(methodName, error)
    }

    void reset() {
        ID_GENERATOR.set(0)
        elements.clear()
        errors.clear()
    }
}
