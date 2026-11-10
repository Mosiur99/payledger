package dev.mosiur.payledger.payments.idempotency.service.impl;

import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyDecision;
import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyRecord;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import dev.mosiur.payledger.payments.idempotency.repository.IdempotencyKeyRepository;
import dev.mosiur.payledger.payments.idempotency.service.IdempotencyService;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyServiceImpl(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IdempotencyDecision begin(String ownerId, String key, String requestHash) {
        if (repository.insertIfAbsent(ownerId, key, requestHash, clock.instant())) {
            return new IdempotencyDecision.Proceed();
        }
        Optional<IdempotencyRecord> existing = repository.find(ownerId, key);
        if (existing.isEmpty()) {
            // The first request failed and released the key a moment ago; the client may simply retry.
            return new IdempotencyDecision.InProgress();
        }
        IdempotencyRecord record = existing.get();
        if (!record.requestHash().equals(requestHash)) {
            return new IdempotencyDecision.KeyReused();
        }
        if (!record.isCompleted()) {
            return new IdempotencyDecision.InProgress();
        }
        return new IdempotencyDecision.Replay(new StoredResponse(
                record.responseStatus(), record.responseContentType(), record.responseBody()));
    }

    @Override
    @Transactional
    public void complete(String ownerId, String key, StoredResponse response) {
        repository.complete(ownerId, key, response, clock.instant());
    }

    @Override
    @Transactional
    public void release(String ownerId, String key) {
        repository.deleteUnfinished(ownerId, key);
    }
}
