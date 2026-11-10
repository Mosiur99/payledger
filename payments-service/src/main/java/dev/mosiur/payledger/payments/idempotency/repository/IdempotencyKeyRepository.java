package dev.mosiur.payledger.payments.idempotency.repository;

import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyRecord;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyKeyRepository {

    private final JdbcClient jdbc;

    public IdempotencyKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns false when the key already exists; a concurrent insert waits until the other one commits. */
    public boolean insertIfAbsent(String ownerId, String key, String requestHash, Instant now) {
        return jdbc.sql("""
                INSERT INTO idempotency_keys (owner_id, idempotency_key, request_hash, created_at)
                VALUES (:owner, :key, :hash, :now)
                ON CONFLICT (owner_id, idempotency_key) DO NOTHING
                """)
                .param("owner", ownerId)
                .param("key", key)
                .param("hash", requestHash)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    public Optional<IdempotencyRecord> find(String ownerId, String key) {
        return jdbc.sql("""
                SELECT owner_id, idempotency_key, request_hash, response_status,
                       response_content_type, response_body, created_at
                FROM idempotency_keys
                WHERE owner_id = :owner AND idempotency_key = :key
                """)
                .param("owner", ownerId)
                .param("key", key)
                .query((rs, row) -> new IdempotencyRecord(
                        rs.getString("owner_id"),
                        rs.getString("idempotency_key"),
                        rs.getString("request_hash"),
                        (Integer) rs.getObject("response_status"),
                        rs.getString("response_content_type"),
                        rs.getString("response_body"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    public void complete(String ownerId, String key, StoredResponse response, Instant now) {
        jdbc.sql("""
                UPDATE idempotency_keys
                SET response_status = :status, response_content_type = :contentType,
                    response_body = :body, completed_at = :now
                WHERE owner_id = :owner AND idempotency_key = :key AND response_status IS NULL
                """)
                .param("status", response.status())
                .param("contentType", response.contentType())
                .param("body", response.body())
                .param("now", Timestamp.from(now))
                .param("owner", ownerId)
                .param("key", key)
                .update();
    }

    public void deleteUnfinished(String ownerId, String key) {
        jdbc.sql("""
                DELETE FROM idempotency_keys
                WHERE owner_id = :owner AND idempotency_key = :key AND response_status IS NULL
                """)
                .param("owner", ownerId)
                .param("key", key)
                .update();
    }
}
