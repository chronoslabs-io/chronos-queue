package com.chronoslabs.transactionalqueue;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.InstanceOfAssertFactory;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface TransactionalQueueInstanceOfAssertFactory extends InstanceOfAssertFactories {

  static <E>
      InstanceOfAssertFactory<TransactionalQueueElement, TransactionalQueueElementAssert<E>>
          transactionalQueueElement(Class<E> payloadType) {
    return new InstanceOfAssertFactory<>(
        TransactionalQueueElement.class, new Class[] {payloadType}, Assertions::<E>assertThat);
  }

  static <E>
      InstanceOfAssertFactory<TransactionalQueueError, TransactionalQueueErrorAssert<E>>
          transactionalQueueError(Class<E> elementType) {
    return new InstanceOfAssertFactory<>(
        TransactionalQueueError.class, new Class[] {elementType}, Assertions::<E>assertThat);
  }
}
