package dev.mosiur.payledger.payments.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import dev.mosiur.payledger.payments.outbox.relay.EventPublisher;
import dev.mosiur.payledger.payments.outbox.relay.OutboxRelay;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the relay against real PostgreSQL row locks, with Kafka replaced by a recording publisher. */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OutboxRelayIT {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private EventPublisher publisher;

    private final Queue<UUID> sent = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void recordWhatIsSent() {
        when(publisher.publish(any())).thenAnswer(call -> {
            sent.add(call.<OutboxEvent>getArgument(0).getId());
            return CompletableFuture.completedFuture(null);
        });
        drain();
        sent.clear();
    }

    @Test
    void skipLockedGivesConcurrentRelaysDifferentRows() throws Exception {
        Set<UUID> created = createEvents(10);
        CountDownLatch firstHoldsLocks = new CountDownLatch(1);
        CountDownLatch secondIsDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<UUID>> first = pool.submit(() -> transactionTemplate.execute(status -> {
                List<UUID> ids = ids(repository.lockNextBatch(5));
                firstHoldsLocks.countDown();
                await(secondIsDone);
                return ids;
            }));
            assertThat(firstHoldsLocks.await(10, TimeUnit.SECONDS)).isTrue();
            Future<List<UUID>> second = pool.submit(() -> transactionTemplate.execute(status -> {
                List<UUID> ids = ids(repository.lockNextBatch(5));
                secondIsDone.countDown();
                return ids;
            }));

            List<UUID> secondIds = second.get(10, TimeUnit.SECONDS);
            List<UUID> firstIds = first.get(10, TimeUnit.SECONDS);
            assertThat(firstIds).hasSize(5).doesNotContainAnyElementsOf(secondIds);
            assertThat(secondIds).hasSize(5);
            assertThat(created).containsExactlyInAnyOrderElementsOf(concat(firstIds, secondIds));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRelaysSendEveryEventExactlyOnce() throws Exception {
        Set<UUID> created = createEvents(350);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> relays = IntStream.range(0, 4)
                    .mapToObj(i -> pool.submit(this::drain))
                    .collect(Collectors.toList());
            for (Future<?> running : relays) {
                running.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Map<UUID, Long> sendsPerEvent = sent.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(sendsPerEvent.keySet()).containsExactlyInAnyOrderElementsOf(created);
        assertThat(sendsPerEvent.values()).containsOnly(1L);
        assertThat(repository.countByPublishedAtIsNull()).isZero();
    }

    private void drain() {
        int published;
        do {
            published = relay.relayBatch();
        } while (published > 0);
    }

    private Set<UUID> createEvents(int count) {
        return transactionTemplate.execute(status -> IntStream.range(0, count)
                .mapToObj(i -> repository.save(OutboxEvent.pending(
                        "payledger.transactions", "key-" + i, "TransactionPosted", "{\"n\":" + i + "}", Instant.now())))
                .map(OutboxEvent::getId)
                .collect(Collectors.toSet()));
    }

    private static List<UUID> ids(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::getId).toList();
    }

    private static List<UUID> concat(List<UUID> first, List<UUID> second) {
        List<UUID> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
