package io.chronoslabs.queue

import com.github.sviperll.result4j.Result

import java.util.concurrent.ConcurrentHashMap

class TestTransactionalQueueDeadLetterRepository implements TransactionalQueueDeadLetterRepository<TestTransactionalQueueElement> {

    private final Map<Long, TestTransactionalQueueElement> elements = new ConcurrentHashMap<>()
    private final Map<String, TransactionalQueueError<?>> errors = [
        "insert": null,
    ] as Map<String, TransactionalQueueError<?>>

    @Override
    Result<Long, TransactionalQueueError<TestTransactionalQueueElement>> insert(TestTransactionalQueueElement element) {
        TransactionalQueueError<TestTransactionalQueueElement> errorResult = errors.get("insert") as TransactionalQueueError<TestTransactionalQueueElement>
        if (errorResult != null) {
            return Result.error(errorResult)
        }
        elements.put(element.id(), element)
        return Result.success(element.id())
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
        elements.clear()
        errors.clear()
    }
}
