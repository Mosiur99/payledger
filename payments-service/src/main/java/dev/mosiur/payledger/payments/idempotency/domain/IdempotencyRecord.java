package dev.mosiur.payledger.payments.idempotency.domain;

import java.time.Instant;

public record IdempotencyRecord(
        String ownerId,
        String key,
        String requestHash,
        Integer responseStatus,
        String responseContentType,
        String responseBody,
        Instant createdAt) {

    public boolean isCompleted() {
        return responseStatus != null;
    }
}
