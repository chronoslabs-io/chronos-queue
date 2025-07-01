package io.chronoslabs.queue;

import static java.util.Objects.requireNonNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MetricHandler {
  private static final Logger logger = LoggerFactory.getLogger(MetricHandler.class);
  private static final String LOG_START =
      "An error occurred while incrementing {} metric with tags {}={}, {}={}";
  private static final String LOG_WITH_2_TAGS = LOG_START + ".";
  private static final String LOG_WITH_3_TAGS = LOG_START + ", {}={}.";

  private static final String METRIC_NAME_PREFIX = "TransactionalQueue";
  private static final String METRIC_NAME_ERROR = METRIC_NAME_PREFIX + ".error";
  private static final String METRIC_NAME_MOVE_TO_DEAD_LETTER_QUEUE =
      METRIC_NAME_PREFIX + ".moveToDeadLetterQueue";
  private static final String METRIC_NAME_SUCCESS = METRIC_NAME_PREFIX + ".success";

  static final String METRIC_TAG_DISPATCH_COUNT = "dispatch-count";
  private static final String METRIC_TAG_ERROR_CLASS = "error-class";
  private static final String METRIC_TAG_ERROR_TYPE = "error-type";
  private static final String METRIC_TAG_QUEUE_NAME = "queue-name";

  private final MeterRegistry meterRegistry;
  private final String queueName;

  MetricHandler(MeterRegistry meterRegistry, String queueName) {
    this.meterRegistry = requireNonNull(meterRegistry, "MetricHandler.meterRegistry");
    this.queueName = requireNonNull(queueName, "MetricHandler.queueName");
  }

  <E> void registerQueueError(TransactionalQueueError<E> error) {
    if (error.cause() == null) {
      registerError(error.type());
    } else {
      registerError(error.cause(), error.type());
    }
  }

  void registerDispatchSuccess(Duration duration, int dispatchCount) {
    try {
      Timer.builder(METRIC_NAME_SUCCESS)
          .tag(METRIC_TAG_DISPATCH_COUNT, String.valueOf(dispatchCount))
          .tag(METRIC_TAG_QUEUE_NAME, queueName)
          .register(meterRegistry)
          .record(duration);
    } catch (Exception exception) {
      logger
          .atWarn()
          .setCause(exception)
          .setMessage(LOG_WITH_2_TAGS)
          .addArgument(METRIC_NAME_SUCCESS)
          .addArgument(METRIC_TAG_DISPATCH_COUNT)
          .addArgument(dispatchCount)
          .addArgument(METRIC_TAG_QUEUE_NAME)
          .addArgument(queueName)
          .log();
    }
  }

  void registerRollbackError(Throwable cause) {
    registerError(cause, "database-rollback");
  }

  void registerError(Throwable cause, String errorType) {
    if (cause == null) {
      registerError(errorType);
      return;
    }
    try {
      Counter.builder(METRIC_NAME_ERROR)
          .tag(METRIC_TAG_ERROR_CLASS, cause.getClass().getCanonicalName())
          .tag(METRIC_TAG_ERROR_TYPE, errorType)
          .tag(METRIC_TAG_QUEUE_NAME, queueName)
          .register(meterRegistry)
          .increment();
    } catch (Exception exception) {
      logger
          .atWarn()
          .setCause(exception)
          .setMessage(LOG_WITH_3_TAGS)
          .addArgument(METRIC_NAME_ERROR)
          .addArgument(METRIC_TAG_ERROR_CLASS)
          .addArgument(cause.getClass().getCanonicalName())
          .addArgument(METRIC_TAG_ERROR_TYPE)
          .addArgument(errorType)
          .addArgument(METRIC_TAG_QUEUE_NAME)
          .addArgument(queueName)
          .log();
    }
  }

  void registerError(String errorType) {
    try {
      Counter.builder(METRIC_NAME_ERROR)
          .tag(METRIC_TAG_ERROR_TYPE, errorType)
          .tag(METRIC_TAG_QUEUE_NAME, queueName)
          .register(meterRegistry)
          .increment();
    } catch (Exception exception) {
      logger
          .atWarn()
          .setCause(exception)
          .setMessage(LOG_WITH_2_TAGS)
          .addArgument(METRIC_NAME_ERROR)
          .addArgument(METRIC_TAG_ERROR_TYPE)
          .addArgument(errorType)
          .addArgument(METRIC_TAG_QUEUE_NAME)
          .addArgument(queueName)
          .log();
    }
  }

  void registerMoveToDeadLetterQueue(int dispatchCount) {
    try {
      Counter.builder(METRIC_NAME_MOVE_TO_DEAD_LETTER_QUEUE)
          .tag(METRIC_TAG_DISPATCH_COUNT, String.valueOf(dispatchCount))
          .tag(METRIC_TAG_QUEUE_NAME, queueName)
          .register(meterRegistry)
          .increment();
    } catch (Exception exception) {
      logger
          .atWarn()
          .setCause(exception)
          .setMessage(LOG_WITH_2_TAGS)
          .addArgument(METRIC_NAME_MOVE_TO_DEAD_LETTER_QUEUE)
          .addArgument(METRIC_TAG_DISPATCH_COUNT)
          .addArgument(dispatchCount)
          .addArgument(METRIC_TAG_QUEUE_NAME)
          .addArgument(queueName)
          .log();
    }
  }
}
