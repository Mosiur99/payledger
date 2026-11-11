package dev.mosiur.payledger.payments.outbox.relay;

import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes committed outbox events at least once. An event is marked published only after Kafka
 * acknowledges it; if the relay crashes between the two, the event is sent again on the next run.
 */
@Service
public class OutboxRelay {

    public static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, EventPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Returns how many events were published; failed ones stay unpublished and are retried next run. */
    @Transactional
    public int relayBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(BATCH_SIZE);
        List<CompletableFuture<?>> sends = batch.stream().map(this::send).toList();

        int published = 0;
        for (int i = 0; i < batch.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                sends.get(i).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished(clock.instant());
                published++;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Outbox event {} was not published and will be retried", event.getId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return published;
    }

    private CompletableFuture<?> send(OutboxEvent event) {
        try {
            return publisher.publish(event);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
