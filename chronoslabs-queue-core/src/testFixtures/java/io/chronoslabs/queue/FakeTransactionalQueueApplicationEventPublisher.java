package io.chronoslabs.queue;

import com.github.sviperll.result4j.Result;
import java.util.ArrayList;
import java.util.List;

public class FakeTransactionalQueueApplicationEventPublisher
    implements TransactionalQueueApplicationEventPublisher {
  private final List<Object> events = new ArrayList<>();
  private TransactionalQueueError<?> error;

  @Override
  @SuppressWarnings("unchecked")
  public <E extends TransactionalQueueElement<?>>
      Result<E, TransactionalQueueError<E>> publishElementAsApplicationEvent(
          E element, String queueName) {
    if (error != null) {
      return Result.error((TransactionalQueueError<E>) error);
    }
    events.add(element);
    return Result.success(element);
  }

  public int count() {
    return events.size();
  }

  public List<Object> getPublishedEvents() {
    return events;
  }

  public boolean hasNoPublishedEvents() {
    return events.isEmpty();
  }

  public void willReturnError(TransactionalQueueError<?> error) {
    this.error = error;
  }

  @SuppressWarnings("PMD.NullAssignment")
  public void reset() {
    events.clear();
    error = null;
  }
}
