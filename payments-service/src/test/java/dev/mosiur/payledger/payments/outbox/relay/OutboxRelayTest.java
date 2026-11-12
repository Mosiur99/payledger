package dev.mosiur.payledger.payments.outbox.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private EventPublisher publisher;

    private OutboxRelay relay;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        relay = new OutboxRelay(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC), registry);
    }

    @Test
    void marksAcknowledgedEventsAsPublished() {
        OutboxEvent first = event("t1");
        OutboxEvent second = event("t2");
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(first, second));
        when(publisher.publish(first)).thenReturn(CompletableFuture.completedFuture(null));
        when(publisher.publish(second)).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.relayBatch()).isEqualTo(2);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void leavesFailedEventsForTheNextRun() {
        OutboxEvent rejected = event("t1");
        OutboxEvent thrown = event("t2");
        OutboxEvent sent = event("t3");
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(rejected, thrown, sent));
        when(publisher.publish(rejected)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(publisher.publish(thrown)).thenThrow(new IllegalStateException("metadata timeout"));
        when(publisher.publish(sent)).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.relayBatch()).isEqualTo(1);
        assertThat(rejected.isPublished()).isFalse();
        assertThat(thrown.isPublished()).isFalse();
        assertThat(sent.isPublished()).isTrue();
        assertThat(registry.get("payledger.outbox.published").counter().count()).isEqualTo(1);
        assertThat(registry.get("payledger.outbox.publish.failures").counter().count()).isEqualTo(2);
    }

    @Test
    void doesNothingWhenThereIsNothingToSend() {
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of());

        assertThat(relay.relayBatch()).isZero();
    }

    private static OutboxEvent event(String key) {
        return OutboxEvent.pending("payledger.transactions", key, "TransactionPosted", "{}", null, NOW);
    }
}
