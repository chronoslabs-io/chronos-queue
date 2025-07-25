package io.chronoslabs.queue;

import com.github.sviperll.result4j.Result;

@FunctionalInterface
public interface TransactionCreator {
  Result<OpenedTransaction, TransactionalQueueError<String>> openTransaction(
      TransactionDefinition transactionDefinition, String queueName);
}
