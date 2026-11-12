package dev.mosiur.payledger.payments.outbox.relay;

import dev.mosiur.payledger.payments.common.observability.TraceParents;
import dev.mosiur.payledger.payments.outbox.domain.OutboxEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TraceParents traceParents;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, TraceParents traceParents) {
        this.kafkaTemplate = kafkaTemplate;
        this.traceParents = traceParents;
    }

    @Override
    public CompletableFuture<?> publish(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        // Consumers deduplicate on the event id, because the relay may send an event more than once.
        record.headers().add(EVENT_ID_HEADER, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
        // Resume the trace of the request that recorded the event; KafkaTemplate's own span and the
        // traceparent header it adds then belong to that trace instead of a new one.
        Span span = traceParents.startChild(event.getTraceParent(), "outbox publish");
        try (Tracer.SpanInScope ignored = traceParents.tracer().withSpan(span)) {
            return kafkaTemplate.send(record).whenComplete((result, error) -> {
                if (error != null) {
                    span.error(error);
                }
                span.end();
            });
        } catch (RuntimeException e) {
            span.error(e);
            span.end();
            throw e;
        }
    }
}
