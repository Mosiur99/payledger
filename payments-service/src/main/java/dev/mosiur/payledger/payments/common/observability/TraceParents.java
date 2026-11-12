package dev.mosiur.payledger.payments.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Saves the current trace as a W3C traceparent string and resumes it later, on another thread. */
@Component
public class TraceParents {

    static final String HEADER = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceParents(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** Returns null when no trace is active, for example in a scheduled job. */
    public String current() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(HEADER);
    }

    /** Starts a span that continues the saved trace, or a new trace when nothing was saved. */
    public Span startChild(String traceParent, String name) {
        Span.Builder builder = traceParent == null
                ? tracer.spanBuilder()
                : propagator.extract(Map.of(HEADER, traceParent), Map::get);
        return builder.name(name).start();
    }

    public Tracer tracer() {
        return tracer;
    }
}
