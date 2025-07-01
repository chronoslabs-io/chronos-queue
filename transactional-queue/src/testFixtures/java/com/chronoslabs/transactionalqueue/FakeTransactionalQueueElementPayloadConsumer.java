package com.chronoslabs.transactionalqueue;

import com.github.sviperll.result4j.Result;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class FakeTransactionalQueueElementPayloadConsumer<E extends TransactionalQueueElement<?>>
    implements TransactionalQueueElementPayloadConsumer<E> {
  public static final Runnable NO_OP_RUNNABLE = () -> {};

  private final Map<String, Runnable> callbacks =
      new ConcurrentHashMap<>(
          Map.of(
              "consumeElementPayload", NO_OP_RUNNABLE,
              "consumeElementPayloadFallback", NO_OP_RUNNABLE));
  private final Map<String, TransactionalQueueError<E>> errors = new ConcurrentHashMap<>();
  private final Deque<E> payloadsStore = new ConcurrentLinkedDeque<>();

  @Override
  public Result<E, TransactionalQueueError<E>> consumeElementPayload(E payload) {
    callbacks.getOrDefault("consumeElementPayload", NO_OP_RUNNABLE).run();

    TransactionalQueueError<E> queueError = errors.get("consumeElementPayload");
    if (queueError != null) {
      return Result.error(queueError);
    }

    payloadsStore.add(payload);
    return Result.success(payload);
  }

  @Override
  public Result<E, TransactionalQueueError<E>> consumeElementPayloadFallback(E payload) {
    callbacks.getOrDefault("consumeElementPayloadFallback", NO_OP_RUNNABLE).run();

    TransactionalQueueError<E> queueError = errors.get("consumeElementPayloadFallback");
    if (queueError != null) {
      return Result.error(queueError);
    }

    return Result.success(payload);
  }

  public List<?> consumedPayloads() {
    return payloadsStore.stream().map(TransactionalQueueElement::payload).toList();
  }

  public int count() {
    return payloadsStore.size();
  }

  public void willRunBeforeConsume(Runnable callback) {
    Optional.ofNullable(callback).ifPresent(c -> this.callbacks.put("consumeElementPayload", c));
  }

  public void willRunBeforeConsumeFallback(Runnable callback) {
    Optional.ofNullable(callback)
        .ifPresent(c -> this.callbacks.put("consumeElementPayloadFallback", c));
  }

  public void consumeElementPayloadWillReturnError(TransactionalQueueError<E> error) {
    this.errors.put("consumeElementPayload", error);
  }

  public void consumeElementPayloadFallbackWillReturnError(TransactionalQueueError<E> error) {
    this.errors.put("consumeElementPayloadFallback", error);
  }

  public void reset() {
    callbacks.clear();
    errors.clear();
    payloadsStore.clear();
  }
}
