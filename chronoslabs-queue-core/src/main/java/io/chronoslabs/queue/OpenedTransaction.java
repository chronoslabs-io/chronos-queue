package io.chronoslabs.queue;

import com.github.sviperll.result4j.Result;

public interface OpenedTransaction {
  <S> Result<S, TransactionalQueueError<String>> commit(S success);

  void rollback();
}
