package dev.mosiur.payledger.payments.outbox.relay;

import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import java.util.concurrent.CompletableFuture;

public interface EventPublisher {

    /** Completes when the broker has acknowledged the event. */
    CompletableFuture<?> publish(OutboxEvent event);
}
