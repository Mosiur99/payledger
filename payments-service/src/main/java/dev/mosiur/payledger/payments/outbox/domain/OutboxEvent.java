package dev.mosiur.payledger.payments.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false, length = 100)
    private String messageKey;

    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "trace_parent", updatable = false, length = 64)
    private String traceParent;

    protected OutboxEvent() {
    }

    private OutboxEvent(String topic, String messageKey, String eventType, String payload, String traceParent,
            Instant createdAt) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.traceParent = traceParent;
        this.createdAt = createdAt;
    }

    public static OutboxEvent pending(String topic, String messageKey, String eventType, String payload,
            String traceParent, Instant now) {
        return new OutboxEvent(topic, messageKey, eventType, payload, traceParent, now);
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getTraceParent() {
        return traceParent;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
