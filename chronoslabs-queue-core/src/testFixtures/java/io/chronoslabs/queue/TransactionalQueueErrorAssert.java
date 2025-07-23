package io.chronoslabs.queue;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;

public class TransactionalQueueErrorAssert<E>
    extends AbstractAssert<TransactionalQueueErrorAssert<E>, TransactionalQueueError<E>> {

  private TransactionalQueueErrorAssert(TransactionalQueueError<E> actual) {
    super(actual, TransactionalQueueErrorAssert.class);
  }

  public static <E> TransactionalQueueErrorAssert<E> assertThat(TransactionalQueueError<E> actual) {
    return new TransactionalQueueErrorAssert<>(actual);
  }

  public TransactionalQueueErrorAssert<E> hasNoCause() {
    isNotNull();

    Assertions.assertThat(actual.cause()).describedAs("Check no Cause").isNull();

    return myself;
  }

  public TransactionalQueueErrorAssert<E> hasCauseThat(
      Consumer<
              ? super
                  AbstractThrowableAssert<? extends AbstractThrowableAssert, ? extends Throwable>>
          assertConsumer) {
    isNotNull();

    assertConsumer.accept(Assertions.assertThat(actual.cause()));

    return myself;
  }

  public TransactionalQueueErrorAssert<E> hasElement(E expected) {
    isNotNull();

    Assertions.assertThat(actual.element()).describedAs("Check Element").isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueErrorAssert<E> hasMessage(String expected) {
    isNotNull();

    Assertions.assertThat(actual.message()).describedAs("Check Message").isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueErrorAssert<E> hasName(String expected) {
    isNotNull();

    Assertions.assertThat(actual.name()).describedAs("Check Name").isEqualTo(expected);

    return myself;
  }

  public TransactionalQueueErrorAssert<E> hasType(String expected) {
    isNotNull();

    Assertions.assertThat(actual.type()).describedAs("Check Type").isEqualTo(expected);

    return myself;
  }
}
