package io.chronoslabs.queue;

import static io.chronoslabs.queue.Assert.requireNonNull;

import com.github.sviperll.result4j.Result;
import java.io.Serial;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public abstract class JdbcClientTransactionalQueueElementRepository<
        P,
        E extends TransactionalQueueElement<P>,
        I extends TransactionalQueueElementToEnqueue<P, E>>
    implements TransactionalQueueElementRepository<P, E, I> {
  private static final Logger log =
      LoggerFactory.getLogger(JdbcClientTransactionalQueueElementRepository.class);

  protected final JdbcClient jdbcClient;
  protected final String queueName;
  protected final RowMapper<E> rowMapper;
  private final String findByIdStatementSql;
  private final String lockForNextDispatchStatementSql;
  private final String deleteStatementSql;

  protected JdbcClientTransactionalQueueElementRepository(
      JdbcClient jdbcClient,
      String queueName,
      String tableName,
      String tableColumns,
      RowMapper<E> rowMapper) {
    this.jdbcClient =
        requireNonNull(jdbcClient, "JdbcClientTransactionalQueueElementRepository.jdbcClient");
    this.queueName = queueName;
    this.findByIdStatementSql = aFindByIdStatementSql(tableName, tableColumns);
    this.lockForNextDispatchStatementSql =
        aLockForNextDispatchStatementSql(tableName, tableColumns);
    this.deleteStatementSql = aDeleteStatementSql(tableName);
    this.rowMapper = rowMapper;
  }

  @Override
  public Result<E, TransactionalQueueError<I>> insert(
      I elementToEnqueue, Instant createdAt, Instant nextDispatchAfter) {
    return insertElement(elementToEnqueue, createdAt, nextDispatchAfter)
        .flatMap(keyHolder -> retrieveInsertedElementId(elementToEnqueue, keyHolder))
        .map(
            id ->
                elementToEnqueue
                    .toTransactionalQueueElementBuilder()
                    .withId(id)
                    .withCreatedAt(createdAt)
                    .withDispatchCount(INITIAL_DISPATCH_COUNT)
                    .withNextDispatchAfter(nextDispatchAfter)
                    .withPayload(elementToEnqueue.payload())
                    .build());
  }

  @Override
  public Result<E, TransactionalQueueError<Long>> findById(long id) {
    try {
      var element = aFindByIdStatementSpec(id).query(rowMapper).single();
      return Result.success(element);
    } catch (Exception exception) {
      log.atError()
          .addArgument(queueName)
          .addArgument(id)
          .setCause(exception)
          .log("Failed to select {} element with id={}.");
      return Result.error(
          TransactionalQueueError.<Long>builder(queueName)
              .withCause(exception)
              .withElement(id)
              .withMessage("Failed to select %s Element with id=%d.".formatted(queueName, id))
              .withType("read-element-error")
              .build());
    }
  }

  @Override
  public Result<Collection<E>, TransactionalQueueError<String>> lockForNextDispatch(
      int batchSize, Instant notDispatchedTill, Instant nextDispatchTime) {
    try {
      var elements =
          aLockForNextDispatchStatementSpec(batchSize, notDispatchedTill, nextDispatchTime)
              .query(rowMapper)
              .list();
      return Result.success(elements);
    } catch (Exception exception) {
      var params =
          "batchSize=%d, notDispatchedTill=%s, nextDispatchTime=%s"
              .formatted(batchSize, notDispatchedTill, nextDispatchTime);
      return Result.error(
          TransactionalQueueError.<String>builder(queueName)
              .withCause(exception)
              .withElement(params)
              .withMessage(
                  "Failed to lock %s Elements for retry dispatch. Params: %s"
                      .formatted(queueName, params))
              .withType("retry-dispatch-lock-error")
              .build());
    }
  }

  @Override
  @SuppressWarnings("java:S1126")
  public Result<E, TransactionalQueueError<E>> delete(E element) {
    try {
      int rowsAffected = aDeleteStatementSpec(element).update();
      if (rowsAffected > 0) {
        return Result.success(element);
      }
      return Result.error(
          TransactionalQueueError.<E>builder(queueName)
              .withElement(element)
              .withMessage(
                  "Failed to delete %s Element with id=%d.".formatted(queueName, element.id()))
              .withType("zero-rows-deleted")
              .build());
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<E>builder(queueName)
              .withCause(exception)
              .withElement(element)
              .withMessage(
                  "Failed to delete %s Element with id=%d.".formatted(queueName, element.id()))
              .withType("delete-element-error")
              .build());
    }
  }

  @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
  private Result<KeyHolder, TransactionalQueueError<I>> insertElement(
      I elementToEnqueue, Instant createdAt, Instant nextDispatchAfter) {
    var keyHolder = new GeneratedKeyHolder();
    try {
      int rowsInserted =
          anInsertStatementSpec(elementToEnqueue, createdAt, nextDispatchAfter).update(keyHolder);

      if (rowsInserted != 1) {
        return Result.error(
            TransactionalQueueError.<I>builder(queueName)
                .withElement(elementToEnqueue)
                .withType("queue-insert-count-rows-inserted")
                .withMessage(
                    "Failed to insert element into Webhook Outbox. Rows inserted: %d."
                        .formatted(rowsInserted))
                .build());
      }
    } catch (Exception exception) {
      log.atError()
          .addArgument(queueName)
          .addArgument(elementToEnqueue)
          .setCause(exception)
          .log("Failed to insert element into {}. Element: {}.");
      return Result.error(
          TransactionalQueueError.<I>builder(queueName)
              .withCause(exception)
              .withElement(elementToEnqueue)
              .withMessage(
                  "An unexpected error occurred during insert element into %s. "
                          .formatted(queueName)
                      + "Error: "
                      + exception.getMessage())
              .withType("queue-insert-error")
              .build());
    }
    return Result.success(keyHolder);
  }

  private Result<Long, TransactionalQueueError<I>> retrieveInsertedElementId(
      I elementToEnqueue, KeyHolder keyHolder) {
    try {
      Object key = Optional.ofNullable(keyHolder.getKeys()).orElseGet(Map::of).get("id");
      if (key == null) {
        return Result.error(
            TransactionalQueueError.<I>builder(queueName)
                .withElement(elementToEnqueue)
                .withType("element-id-null")
                .withMessage("Generated ID for inserted %s Element is null.".formatted(queueName))
                .build());
      }
      if (Long.class.isAssignableFrom(key.getClass())) {
        return Result.success((Long) key);
      }
      return Result.error(
          TransactionalQueueError.<I>builder(queueName)
              .withElement(elementToEnqueue)
              .withType("element-id-not-long")
              .withMessage(
                  "Generated ID for inserted %s Element is %s but should be java.lang.Long."
                      .formatted(queueName, key.getClass().getCanonicalName()))
              .build());
    } catch (Exception exception) {
      return Result.error(
          TransactionalQueueError.<I>builder(queueName)
              .withCause(exception)
              .withElement(elementToEnqueue)
              .withMessage(
                  "An unexpected error occurred during retrieval of generated ID "
                      + "for inserted %s Element. Error: ".formatted(queueName)
                      + exception.getMessage())
              .withType("element-id-error")
              .build());
    }
  }

  protected abstract JdbcClient.StatementSpec anInsertStatementSpec(
      I elementToEnqueue, Instant createdAt, Instant nextDispatchAfter) throws ElementException;

  private static String aFindByIdStatementSql(String tableName, String tableColumns) {
    return """
    SELECT %2$s
    FROM %1$s
    WHERE id = :id
    """
        .formatted(tableName, tableColumns);
  }

  protected JdbcClient.StatementSpec aFindByIdStatementSpec(long id) {
    return jdbcClient.sql(findByIdStatementSql).param("id", id);
  }

  private static String aLockForNextDispatchStatementSql(String tableName, String tableColumns) {
    return """
    WITH locked_records AS (SELECT id
                            FROM %1$s
                            WHERE next_dispatch_after < :notDispatchedTill
                            ORDER BY created_at, id
                            LIMIT :batchSize FOR UPDATE SKIP LOCKED)
    UPDATE %1$s
    SET next_dispatch_after = :nextDispatchTime,
        dispatch_count      = dispatch_count + 1
    WHERE id IN (SELECT id FROM locked_records)
    RETURNING %2$s
    """
        .formatted(tableName, tableColumns);
  }

  protected JdbcClient.StatementSpec aLockForNextDispatchStatementSpec(
      int batchSize, Instant notDispatchedTill, Instant nextDispatchTime) {
    return jdbcClient
        .sql(lockForNextDispatchStatementSql)
        .param("batchSize", batchSize)
        .param("notDispatchedTill", Timestamp.from(notDispatchedTill))
        .param("nextDispatchTime", Timestamp.from(nextDispatchTime));
  }

  private static String aDeleteStatementSql(String tableName) {
    return """
    DELETE FROM %1$s
    WHERE id = :id
      AND dispatch_count = :dispatch_count
    """
        .formatted(tableName);
  }

  protected JdbcClient.StatementSpec aDeleteStatementSpec(E element) {
    return jdbcClient
        .sql(deleteStatementSql)
        .param("id", element.id())
        .param("dispatch_count", element.dispatchCount());
  }

  public static class ElementException extends RuntimeException {
    @Serial private static final long serialVersionUID = 4460657212154651111L;

    public ElementException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
