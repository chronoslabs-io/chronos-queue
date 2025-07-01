package com.chronoslabs.transactionalqueue;

import java.time.Instant;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class TransactionalQueueElementAssert<E>
    extends AbstractAssert<TransactionalQueueElementAssert<E>, TransactionalQueueElement<E>> {

  private TransactionalQueueElementAssert(TransactionalQueueElement<E> actual) {
    super(actual, TransactionalQueueElementAssert.class);
  }

  public static <E> TransactionalQueueElementAssert<E> assertThat(
      TransactionalQueueElement<E> actual) {
    return new TransactionalQueueElementAssert<>(actual);
  }

  public TransactionalQueueElementAssert<E> hasId(long expected) {
    isNotNull();

    Assertions.assertThat(actual.id()).describedAs("Check Id").isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueElementAssert<E> hasId() {
    isNotNull();

    Assertions.assertThat(actual.id()).describedAs("Check Id").isNotNull();

    return myself;
  }

  public TransactionalQueueElementAssert<E> hasPayload(E expected) {
    isNotNull();

    Assertions.assertThat(actual.payload()).describedAs("Check Payload").isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueElementAssert<E> hasCreatedAt(String expected) {
    return hasCreatedAt(Instant.parse(expected));
  }

  public TransactionalQueueElementAssert<E> hasCreatedAt(Instant expected) {
    isNotNull();

    Assertions.assertThat(actual.createdAt()).describedAs("Check Created At").isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueElementAssert<E> hasNextDispatchAfter(String expected) {
    return hasNextDispatchAfter(Instant.parse(expected));
  }

  public TransactionalQueueElementAssert<E> hasNextDispatchAfter(Instant expected) {
    isNotNull();

    Assertions.assertThat(actual.nextDispatchAfter())
        .describedAs("Check Next Dispatch After")
        .isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueElementAssert<E> hasDispatchCount(int expected) {
    isNotNull();

    Assertions.assertThat(actual.dispatchCount())
        .describedAs("Check Dispatch Count")
        .isEqualTo(expected);

    return myself;
  }
}
