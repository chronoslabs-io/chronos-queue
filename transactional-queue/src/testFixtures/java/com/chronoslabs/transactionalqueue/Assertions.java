package com.chronoslabs.transactionalqueue;

import com.github.sviperll.assertj.result4j.ResultAssert;
import com.github.sviperll.result4j.Result;

public class Assertions extends org.assertj.core.api.Assertions
    implements TransactionalQueueInstanceOfAssertFactory {

  public static <R, E> ResultAssert<R, E> assertThat(Result<R, E> actual) {
    return ResultAssert.assertThat(actual);
  }

  public static <E> TransactionalQueueElementAssert<E> assertThat(
      TransactionalQueueElement<E> actual) {
    return TransactionalQueueElementAssert.<E>assertThat(actual);
  }

  public static <E> TransactionalQueueErrorAssert<E> assertThat(TransactionalQueueError<E> actual) {
    return TransactionalQueueErrorAssert.<E>assertThat(actual);
  }

  protected Assertions() {}
}
