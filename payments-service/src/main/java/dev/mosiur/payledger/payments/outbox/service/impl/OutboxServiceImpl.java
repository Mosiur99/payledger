package dev.mosiur.payledger.payments.outbox.service.impl;

import dev.mosiur.payledger.payments.common.observability.TraceParents;
import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import dev.mosiur.payledger.payments.outbox.service.OutboxService;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final TraceParents traceParents;
    private final Clock clock;

    public OutboxServiceImpl(OutboxEventRepository repository, JsonMapper jsonMapper, TraceParents traceParents,
            Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.traceParents = traceParents;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String topic, String messageKey, String eventType, Object payload) {
        repository.save(OutboxEvent.pending(
                topic, messageKey, eventType, jsonMapper.writeValueAsString(payload), traceParents.current(),
                clock.instant()));
    }
}
