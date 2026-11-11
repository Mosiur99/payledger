package dev.mosiur.payledger.payments.outbox.relay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnBooleanProperty(name = "payledger.outbox.relay.enabled", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${payledger.outbox.relay.interval:500ms}")
    public void relayPendingEvents() {
        // A full batch means more may be waiting, so keep going instead of sleeping until the next tick.
        int published;
        do {
            published = relay.relayBatch();
        } while (published == OutboxRelay.BATCH_SIZE);
    }
}
