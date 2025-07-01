package com.chronoslabs.transactionalqueue;

import com.github.sviperll.result4j.Result;
import org.springframework.context.ApplicationEventPublisher;

public class SpringTransactionalQueueApplicationEventPublisher
    implements TransactionalQueueApplicationEventPublisher {
  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringTransactionalQueueApplicationEventPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public <E extends TransactionalQueueElement<?>>
      Result<E, TransactionalQueueError<E>> publishElementAsApplicationEvent(
          E element, String queueName) {
    try {
      applicationEventPublisher.publishEvent(element);
      return Result.success(element);
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<E>builder(queueName)
              .withCause(exception)
              .withElement(element)
              .withMessage("Failed to publish element %s as application event.".formatted(element))
              .withType("application-event-publication-error")
              .build());
    }
  }
}
