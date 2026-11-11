package dev.mosiur.payledger.payments.outbox.service;

public interface OutboxService {

    /** Stores an event in the caller's transaction; it is published to Kafka only if that transaction commits. */
    void record(String topic, String messageKey, String eventType, Object payload);
}
