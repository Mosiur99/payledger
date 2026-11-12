package dev.mosiur.payledger.payments.outbox.relay;

import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
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
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxRelay(OutboxEventRepository repository, EventPublisher publisher, Clock clock, MeterRegistry registry) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.publishedCounter = Counter.builder("payledger.outbox.published")
                .description("Outbox events acknowledged by Kafka")
                .register(registry);
        this.failedCounter = Counter.builder("payledger.outbox.publish.failures")
                .description("Outbox sends that failed and will be retried")
                .register(registry);
        Gauge.builder("payledger.outbox.pending", repository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published")
                .register(registry);
        Gauge.builder("payledger.outbox.oldest.pending.age", this, OutboxRelay::oldestPendingAgeSeconds)
                .description("Age of the oldest unpublished event; grows when the relay or Kafka is stuck")
                .baseUnit("seconds")
                .register(registry);
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
                publishedCounter.increment();
            } catch (ExecutionException | TimeoutException e) {
                failedCounter.increment();
                log.warn("Outbox event {} was not published and will be retried", event.getId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return published;
    }

    private double oldestPendingAgeSeconds() {
        Instant oldest = repository.oldestPendingCreatedAt();
        return oldest == null ? 0 : Duration.between(oldest, clock.instant()).toMillis() / 1000.0;
    }

    private CompletableFuture<?> send(OutboxEvent event) {
        try {
            return publisher.publish(event);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
