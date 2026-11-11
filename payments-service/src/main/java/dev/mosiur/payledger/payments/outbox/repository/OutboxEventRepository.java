package dev.mosiur.payledger.payments.outbox.repository;

import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished events. SKIP LOCKED lets several relay instances run at once:
     * each one gets different rows instead of waiting for, or re-sending, rows another instance holds.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int limit);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByMessageKey(String messageKey);
}
