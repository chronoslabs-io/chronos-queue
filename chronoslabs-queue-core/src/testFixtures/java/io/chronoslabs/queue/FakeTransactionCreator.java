package io.chronoslabs.queue;

import com.github.sviperll.result4j.Result;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeTransactionCreator implements TransactionCreator {
  private final Map<String, TransactionalQueueError<String>> errors;

  public FakeTransactionCreator() {
    this.errors = new ConcurrentHashMap<>();
  }

  @Override
  public Result<OpenedTransaction, TransactionalQueueError<String>> openTransaction(
      TransactionDefinition transactionDefinition, String queueName) {
    var error = errors.get("openTransaction");
    if (error != null) {
      return Result.error(error);
    }

    return Result.success(new FakeOpenedTransaction());
  }

  public void willReturnError(String methodName, TransactionalQueueError<String> error) {
    errors.put(methodName, error);
  }

  public void reset() {
    errors.clear();
  }

  private class FakeOpenedTransaction implements OpenedTransaction {

    private FakeOpenedTransaction() {}

    @Override
    public <S> Result<S, TransactionalQueueError<String>> commit(S success) {
      var error = errors.get("commit");
      if (error != null) {
        return Result.error(error);
      }

      return Result.success(success);
    }

    @Override
    public void rollback() {
      // No-op
    }
  }
}
