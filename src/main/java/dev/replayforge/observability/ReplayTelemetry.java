package dev.replayforge.observability;

import dev.replayforge.invariants.InvariantViolation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import java.util.UUID;

public final class ReplayTelemetry {
    private final MeterRegistry registry;
    private final Counter events, faults, violations;
    private final Timer duration;
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("dev.replayforge.replay");

    public ReplayTelemetry(MeterRegistry registry) {
        this.registry = registry;
        events = registry.counter("replayforge.replay.events.processed");
        faults = registry.counter("replayforge.replay.faults.injected");
        violations = registry.counter("replayforge.replay.invariant.violations");
        duration = registry.timer("replayforge.replay.duration");
    }
    public Run start(UUID correlationId, long seed, int sourceEvents) {
        Span span = tracer.spanBuilder("replay.execute").startSpan();
        span.setAttribute("replay.correlation_id", correlationId.toString());
        span.setAttribute("replay.seed", seed); span.setAttribute("replay.source_event_count", sourceEvents);
        return new Run(span, Timer.start(registry), sourceEvents);
    }
    public void eventProcessed() { events.increment(); }
    public void faultInjected() { faults.increment(); }
    public void violations(List<InvariantViolation> found) { violations.increment(found.size()); }

    public final class Run implements AutoCloseable {
        private final Span span; private final Timer.Sample sample; private final int sourceEvents;
        private Run(Span span, Timer.Sample sample, int sourceEvents) { this.span = span; this.sample = sample; this.sourceEvents = sourceEvents; }
        public Span span() { return span; }
        public void close() {
            long nanos = sample.stop(duration);
            double seconds = nanos / 1_000_000_000d;
            span.setAttribute("replay.duration_ms", nanos / 1_000_000d);
            span.setAttribute("replay.throughput.events_per_second", seconds == 0 ? 0 : sourceEvents / seconds);
            registry.summary("replayforge.replay.throughput").record(seconds == 0 ? 0 : sourceEvents / seconds);
            span.end();
        }
    }
}
