package com.chronoslabs.transactionalqueue;

import static com.chronoslabs.transactionalqueue.Assert.requireNonNull;

import com.github.sviperll.result4j.Result;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class SpringTransactionCreator implements TransactionCreator {
  private static final Logger logger = LoggerFactory.getLogger(SpringTransactionCreator.class);

  private final MeterRegistry meterRegistry;
  private final PlatformTransactionManager transactionManager;

  public SpringTransactionCreator(
      MeterRegistry meterRegistry, PlatformTransactionManager transactionManager) {
    this.meterRegistry = requireNonNull(meterRegistry, "SpringTransactionCreator.meterRegistry");
    this.transactionManager =
        requireNonNull(transactionManager, "SpringTransactionCreator.transactionManager");
  }

  @Override
  public Result<OpenedTransaction, TransactionalQueueError<String>> openTransaction(
      TransactionDefinition transactionDefinition, String queueName) {
    try {
      var definition = new DefaultTransactionDefinition();
      definition.setPropagationBehaviorName(transactionDefinition.name());
      return Result.success(
          new SpringOpenedTransaction(transactionManager.getTransaction(definition), queueName));
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<String>builder(queueName)
              .withCause(exception)
              .withElement(queueName)
              .withMessage(
                  "Failed to open transaction with propagation behaviour %s for %s."
                      .formatted(transactionDefinition, queueName))
              .withType("open-transaction-error")
              .build());
    }
  }

  class SpringOpenedTransaction implements OpenedTransaction {
    private final MetricHandler metricHandler;
    private final TransactionStatus status;
    private final String queueName;

    private SpringOpenedTransaction(TransactionStatus status, String queueName) {
      this.metricHandler = new MetricHandler(meterRegistry, queueName);
      this.status = status;
      this.queueName = queueName;
    }

    @Override
    public <S> Result<S, TransactionalQueueError<String>> commit(S success) {
      try {
        transactionManager.commit(status);
        return Result.success(success);
      } catch (Exception exception) {
        logger
            .atError()
            .addArgument(status.getTransactionName())
            .addArgument(queueName)
            .setCause(exception)
            .log("Failed to commit transaction {} for {}.");
        return Result.error(
            TransactionalQueueError.<String>builder(queueName)
                .withCause(exception)
                .withElement(queueName)
                .withMessage(
                    "Failed to commit transaction %s for %s."
                        .formatted(status.getTransactionName(), queueName))
                .withType("commit-transaction-error")
                .build());
      }
    }

    @Override
    public void rollback() {
      try {
        transactionManager.rollback(status);
      } catch (Exception exception) {
        logger
            .atError()
            .addArgument(status.getTransactionName())
            .addArgument(queueName)
            .setCause(exception)
            .log("Failed to rollback transaction {} for {}.");
        metricHandler.registerRollbackError(exception);
      }
    }
  }
}
