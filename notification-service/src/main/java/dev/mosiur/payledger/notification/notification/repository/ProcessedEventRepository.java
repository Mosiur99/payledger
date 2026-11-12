package dev.mosiur.payledger.notification.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {

    private final JdbcClient jdbc;

    public ProcessedEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns false when the event was already processed. A concurrent insert of the same id waits
     * for the other transaction, so two deliveries can never both claim the event.
     */
    public boolean markProcessed(UUID eventId, String eventType, Instant now) {
        return jdbc.sql("""
                INSERT INTO processed_events (event_id, event_type, processed_at)
                VALUES (:id, :type, :now)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("id", eventId)
                .param("type", eventType)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    public boolean exists(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", eventId)
                .query(Long.class)
                .single() > 0;
    }
}
