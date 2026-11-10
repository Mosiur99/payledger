package dev.mosiur.payledger.payments.idempotency.domain;

/** What to do with a request, given what is already stored for its idempotency key. */
public sealed interface IdempotencyDecision {

    /** First time this key is seen: run the request. */
    record Proceed() implements IdempotencyDecision {
    }

    /** The same request already finished: send back its response. */
    record Replay(StoredResponse response) implements IdempotencyDecision {
    }

    /** The same request is still running. */
    record InProgress() implements IdempotencyDecision {
    }

    /** The key was used before for a different request. */
    record KeyReused() implements IdempotencyDecision {
    }
}
