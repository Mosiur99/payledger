package dev.mosiur.payledger.payments.idempotency.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyRecord;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfiguration.class, IdempotencyKeyRepository.class})
class IdempotencyKeyRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Autowired
    private IdempotencyKeyRepository repository;

    @Test
    void insertsAKeyOnlyOncePerOwner() {
        assertThat(repository.insertIfAbsent("ayesha", "key-1", HASH, NOW)).isTrue();
        assertThat(repository.insertIfAbsent("ayesha", "key-1", HASH, NOW)).isFalse();
        assertThat(repository.insertIfAbsent("rahim", "key-1", HASH, NOW)).isTrue();
    }

    @Test
    void storesTheResponseOnce() {
        repository.insertIfAbsent("ayesha", "key-2", HASH, NOW);

        repository.complete("ayesha", "key-2", new StoredResponse(201, "application/json", "{\"id\":1}"), NOW);
        repository.complete("ayesha", "key-2", new StoredResponse(500, "text/plain", "late"), NOW);

        IdempotencyRecord record = repository.find("ayesha", "key-2").orElseThrow();
        assertThat(record.isCompleted()).isTrue();
        assertThat(record.responseStatus()).isEqualTo(201);
        assertThat(record.responseBody()).isEqualTo("{\"id\":1}");
    }

    @Test
    void deletesOnlyAnUnfinishedKey() {
        repository.insertIfAbsent("ayesha", "running", HASH, NOW);
        repository.insertIfAbsent("ayesha", "done", HASH, NOW);
        repository.complete("ayesha", "done", new StoredResponse(201, "application/json", "{}"), NOW);

        repository.deleteUnfinished("ayesha", "running");
        repository.deleteUnfinished("ayesha", "done");

        assertThat(repository.find("ayesha", "running")).isEmpty();
        assertThat(repository.find("ayesha", "done")).isPresent();
    }

    @Test
    void reclaimsOnlyAnUnfinishedKeyOlderThanTheCutoff() {
        Instant cutoff = NOW.minusSeconds(60);
        repository.insertIfAbsent("ayesha", "dead", HASH, NOW.minusSeconds(120));
        repository.insertIfAbsent("ayesha", "alive", HASH, NOW.minusSeconds(10));
        repository.insertIfAbsent("ayesha", "finished", HASH, NOW.minusSeconds(120));
        repository.complete("ayesha", "finished", new StoredResponse(201, "application/json", "{}"), NOW);

        assertThat(repository.reclaimAbandoned("ayesha", "dead", cutoff, NOW)).isTrue();
        assertThat(repository.reclaimAbandoned("ayesha", "dead", cutoff, NOW)).isFalse();
        assertThat(repository.reclaimAbandoned("ayesha", "alive", cutoff, NOW)).isFalse();
        assertThat(repository.reclaimAbandoned("ayesha", "finished", cutoff, NOW)).isFalse();
        assertThat(repository.find("ayesha", "dead").orElseThrow().createdAt()).isEqualTo(NOW);
    }
}
