package dev.mosiur.payledger.payments.idempotency.service;

import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyDecision;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import java.time.Duration;

public interface IdempotencyService {

    /**
     * How long a key may stay unfinished before a retry can take it over. A request's transaction times out
     * well before this, so an older unfinished key belongs to a request that died without moving money.
     */
    Duration ABANDONED_AFTER = Duration.ofMinutes(1);

    IdempotencyDecision begin(String ownerId, String key, String requestHash);

    void complete(String ownerId, String key, StoredResponse response);

    /** Frees the key after a server error so the client can retry with it. */
    void release(String ownerId, String key);
}
