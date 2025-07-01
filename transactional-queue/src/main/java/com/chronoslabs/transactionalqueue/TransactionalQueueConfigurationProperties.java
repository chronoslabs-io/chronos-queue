package com.chronoslabs.transactionalqueue;

import static com.chronoslabs.transactionalqueue.Assert.isTrue;

import java.time.Duration;

public record TransactionalQueueConfigurationProperties(
    Duration lockTimeout,
    int maxDispatchCount,
    int retryDispatchBatchSize,
    Duration retryScheduledRateDelay) {

  void validate(String queueName) {
    isTrue(
        lockTimeout.isPositive(),
        "Configuration property 'lock-timeout' of queue %s must be greater than zero."
            .formatted(queueName));
    isTrue(
        maxDispatchCount > 0,
        "Configuration property 'max-dispatch-count' of queue %s must be greater than zero."
            .formatted(queueName));
    isTrue(
        retryDispatchBatchSize > 0,
        "Configuration property 'retry-dispatch-batch-size' of queue %s must be greater than zero."
            .formatted(queueName));
    isTrue(
        retryScheduledRateDelay.isPositive(),
        "Configuration property 'retry-scheduled-rate-delay' of queue %s must be greater than zero."
            .formatted(queueName));
  }

  public static TransactionalQueueConfigurationProperties defaultConfiguration() {
    return new TransactionalQueueConfigurationProperties(
        Duration.ofSeconds(10), 3, 10, Duration.ofMillis(100));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Duration lockTimeout;
    private int maxDispatchCount;
    private int retryDispatchBatchSize;
    private Duration retryScheduledRateDelay;

    private Builder() {}

    public Builder withLockTimeout(Duration lockTimeout) {
      this.lockTimeout = lockTimeout;
      return this;
    }

    public Builder withMaxDispatchCount(int maxDispatchCount) {
      this.maxDispatchCount = maxDispatchCount;
      return this;
    }

    public Builder withRetryDispatchBatchSize(int retryDispatchBatchSize) {
      this.retryDispatchBatchSize = retryDispatchBatchSize;
      return this;
    }

    public Builder withRetryScheduledRateDelay(Duration retryScheduledRateDelay) {
      this.retryScheduledRateDelay = retryScheduledRateDelay;
      return this;
    }

    public TransactionalQueueConfigurationProperties build() {
      return new TransactionalQueueConfigurationProperties(
          lockTimeout, maxDispatchCount, retryDispatchBatchSize, retryScheduledRateDelay);
    }
  }
}
