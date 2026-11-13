package dev.mosiur.payledger.payments.idempotency.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyDecision;
import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyRecord;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import dev.mosiur.payledger.payments.idempotency.repository.IdempotencyKeyRepository;
import dev.mosiur.payledger.payments.idempotency.service.IdempotencyService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void proceedsWithANewKey() {
        when(repository.insertIfAbsent("ayesha", "k", HASH, NOW)).thenReturn(true);

        assertThat(service.begin("ayesha", "k", HASH)).isInstanceOf(IdempotencyDecision.Proceed.class);
    }

    @Test
    void replaysAFinishedRequest() {
        givenExisting(new IdempotencyRecord("ayesha", "k", HASH, 201, "application/json", "{}", NOW));

        assertThat(service.begin("ayesha", "k", HASH))
                .isEqualTo(new IdempotencyDecision.Replay(new StoredResponse(201, "application/json", "{}")));
    }

    @Test
    void reportsARequestThatIsStillRunning() {
        givenExisting(new IdempotencyRecord("ayesha", "k", HASH, null, null, null, NOW));

        assertThat(service.begin("ayesha", "k", HASH)).isInstanceOf(IdempotencyDecision.InProgress.class);
    }

    @Test
    void takesOverAKeyWhoseRequestDied() {
        givenExisting(new IdempotencyRecord("ayesha", "k", HASH, null, null, null, NOW.minusSeconds(300)));
        when(repository.reclaimAbandoned("ayesha", "k", NOW.minus(IdempotencyService.ABANDONED_AFTER), NOW))
                .thenReturn(true);

        assertThat(service.begin("ayesha", "k", HASH)).isInstanceOf(IdempotencyDecision.Proceed.class);
    }

    @Test
    void neverTakesOverAKeyForADifferentRequest() {
        givenExisting(new IdempotencyRecord("ayesha", "k", "b".repeat(64), null, null, null, NOW.minusSeconds(300)));

        assertThat(service.begin("ayesha", "k", HASH)).isInstanceOf(IdempotencyDecision.KeyReused.class);
        verify(repository, never()).reclaimAbandoned(any(), any(), any(), any());
    }

    @Test
    void detectsAKeyReusedForADifferentRequest() {
        givenExisting(new IdempotencyRecord("ayesha", "k", "b".repeat(64), 201, "application/json", "{}", NOW));

        assertThat(service.begin("ayesha", "k", HASH)).isInstanceOf(IdempotencyDecision.KeyReused.class);
    }

    @Test
    void treatsAJustReleasedKeyAsInProgress() {
        when(repository.insertIfAbsent(anyString(), anyString(), anyString(), any())).thenReturn(false);
        when(repository.find("ayesha", "k")).thenReturn(Optional.empty());

        assertThat(service.begin("ayesha", "k", HASH)).isInstanceOf(IdempotencyDecision.InProgress.class);
    }

    private void givenExisting(IdempotencyRecord record) {
        when(repository.insertIfAbsent(anyString(), anyString(), anyString(), any())).thenReturn(false);
        when(repository.find("ayesha", "k")).thenReturn(Optional.of(record));
    }
}
