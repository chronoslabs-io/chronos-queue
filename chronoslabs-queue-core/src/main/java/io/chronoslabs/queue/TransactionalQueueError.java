package io.chronoslabs.queue;

import org.slf4j.Logger;

public record TransactionalQueueError<E>(
    String name, E element, String type, String message, Throwable cause) {

  public void logError(Logger logger) {
    logger
        .atError()
        .addArgument(element)
        .addArgument(name)
        .addArgument(type)
        .addArgument(message)
        .setCause(cause)
        .log("Queue error for {}. Name: {}. Type: {}, Error: {}");
  }

  public <T> TransactionalQueueError<T> withAnotherElement(T element) {
    return TransactionalQueueError.<T>builder()
        .withName(name)
        .withElement(element)
        .withType(type)
        .withMessage(message)
        .withCause(cause)
        .build();
  }

  public static <E> Builder<E> builder(String name) {
    return TransactionalQueueError.<E>builder().withName(name);
  }

  private static <E> Builder<E> builder() {
    return new Builder<>();
  }

  public static class Builder<E> {
    private String name;
    private E element;
    private String type;
    private String message;
    private Throwable cause;

    private Builder() {}

    private Builder<E> withName(String name) {
      this.name = name;
      return this;
    }

    public Builder<E> withElement(E element) {
      this.element = element;
      return this;
    }

    public Builder<E> withType(String type) {
      this.type = type;
      return this;
    }

    public Builder<E> withMessage(String message) {
      this.message = message;
      return this;
    }

    public Builder<E> withCause(Throwable cause) {
      this.cause = cause;
      return this;
    }

    public TransactionalQueueError<E> build() {
      return new TransactionalQueueError<>(name, element, type, message, cause);
    }
  }
}
