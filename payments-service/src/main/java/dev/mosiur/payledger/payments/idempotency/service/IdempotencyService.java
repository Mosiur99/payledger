package dev.mosiur.payledger.payments.idempotency.service;

import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyDecision;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;

public interface IdempotencyService {

    IdempotencyDecision begin(String ownerId, String key, String requestHash);

    void complete(String ownerId, String key, StoredResponse response);

    /** Frees the key after a server error so the client can retry with it. */
    void release(String ownerId, String key);
}
