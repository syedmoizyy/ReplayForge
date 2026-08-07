package dev.replayforge.replay;

import dev.replayforge.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.replayforge.divergence.*;
import dev.replayforge.invariants.*;
import dev.replayforge.observability.ReplayTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class DeterministicReplayEngine {
    private static final Instant FIXED_EPOCH = Instant.parse("2000-01-01T00:00:00Z");
    private final ReservationReplayReducer reducer = new ReservationReplayReducer();
    private final InvariantEngine invariants;
    private final DivergenceReporter reporter;
    private final ReplayTelemetry telemetry;

    public DeterministicReplayEngine() {
        this(InvariantEngine.standard(), new DivergenceReporter(new ObjectMapper()), new ReplayTelemetry(new SimpleMeterRegistry()));
    }

    public DeterministicReplayEngine(InvariantEngine invariants, DivergenceReporter reporter, ReplayTelemetry telemetry) {
        this.invariants = invariants; this.reporter = reporter; this.telemetry = telemetry;
    }

    public ReplayExecution execute(List<DomainEvent> source, long checkpoint, long seed, ReplayRun.ClockMode clockMode) {
        if (source.isEmpty()) throw new ReplayValidationException("Source trace is empty");
        if (checkpoint < 0) throw new ReplayValidationException("checkpoint must be zero or positive");
        long maximumSequence = source.stream().mapToLong(DomainEvent::sequenceNumber).max().orElseThrow();
        if (checkpoint > maximumSequence) throw new ReplayValidationException(
                "checkpoint " + checkpoint + " exceeds source maximum sequence " + maximumSequence);

        try (ReplayTelemetry.Run run = telemetry.start(source.getFirst().correlationId(), seed, source.size())) {
        SeededDeterministicScheduler scheduler = new SeededDeterministicScheduler(seed);
        Instant initial = clockMode == ReplayRun.ClockMode.SOURCE_RELATIVE ? source.getFirst().occurredAt() : FIXED_EPOCH;
        VirtualClock clock = new VirtualClock(initial);
        Map<UUID, UUID> eventIds = new HashMap<>();
        Map<UUID, UUID> aggregateIds = new HashMap<>();
        UUID replayCorrelationId = scheduler.nextId();
        List<ReplayedEvent> output = new ArrayList<>();
        List<ReplayDecision> decisions = new ArrayList<>();
        ReplayState state = ReplayState.empty();
        ReplayState baselineState = ReplayState.empty();
        List<DomainEvent> seen = new ArrayList<>();
        List<InvariantViolation> violations = new ArrayList<>();
        List<TraceTransition> baselineTransitions = new ArrayList<>();
        List<TraceTransition> replayTransitions = new ArrayList<>();
        int baselineCount = 0;

        for (int index = 0; index < source.size(); index++) {
            DomainEvent original = source.get(index);
            Instant logicalTime = index == 0 ? clock.instant() : clock.advance(Duration.ofMillis(1));
            UUID replayEventId = scheduler.nextId();
            eventIds.put(original.eventId(), replayEventId);
            UUID replayAggregateId = aggregateIds.computeIfAbsent(original.aggregateId(), ignored -> scheduler.nextId());
            ReplayState before = state;
            state = reducer.apply(state, original);
            baselineState = reducer.apply(baselineState, original);
            seen.add(original);
            telemetry.eventProcessed();
            List<InvariantViolation> stepViolations = invariants.evaluate(new InvariantContext(
                    original, before, state, seen, index + 1L, false));
            violations.addAll(stepViolations); telemetry.violations(stepViolations);
            List<String> effects = financialEffects(original);
            baselineTransitions.add(new TraceTransition(index + 1L, original.eventId(), original.eventType(), original.payload(), effects, baselineState));
            replayTransitions.add(new TraceTransition(index + 1L, original.eventId(), original.eventType(), original.payload(), effects, state));
            boolean baseline = original.sequenceNumber() <= checkpoint;
            Map<String, String> detail = new TreeMap<>();
            detail.put("sourceSequenceNumber", Long.toString(original.sequenceNumber()));
            detail.put("sourceEventType", original.eventType());
            detail.put("clockMode", clockMode.name());
            if (baseline) {
                baselineCount++;
                decisions.add(new ReplayDecision(index + 1L, original.eventId(), replayEventId,
                        ReplayDecision.Type.CHECKPOINT_BASELINE_APPLIED, logicalTime, detail));
                continue;
            }
            UUID replayCausationId = original.causationId() == null ? null : eventIds.get(original.causationId());
            Map<String, String> metadata = new TreeMap<>(original.metadata());
            metadata.put("replay.sourceEventId", original.eventId().toString());
            metadata.put("replay.seed", Long.toString(seed));
            DomainEvent replayed = new DomainEvent(replayEventId, original.eventType(), original.schemaVersion(),
                    replayAggregateId, replayCorrelationId, replayCausationId, "replay:" + original.eventId(),
                    original.sequenceNumber(), logicalTime, logicalTime, original.payload(), metadata);
            output.add(new ReplayedEvent(output.size() + 1L, original.eventId(), replayed));
            decisions.add(new ReplayDecision(index + 1L, original.eventId(), replayEventId,
                    ReplayDecision.Type.EVENT_REPLAYED, logicalTime, detail));
        }

        DomainEvent last = source.getLast();
        List<InvariantViolation> completionViolations = invariants.evaluate(new InvariantContext(
                last, state, state, seen, source.size(), true));
        violations.addAll(completionViolations); telemetry.violations(completionViolations);
        DivergenceReport report = reporter.compare(baselineTransitions, replayTransitions, baselineState, state, violations);

        ReplayOutputSummary summary = new ReplayOutputSummary(source.size(), baselineCount, output.size(),
                decisions.size(), digest(state));
        run.span().setAttribute("replay.violation_count", violations.size());
        return new ReplayExecution(output, decisions, state, summary, violations, replayTransitions, report,
                reporter.json(report), reporter.markdown(report));
        }
    }

    private List<String> financialEffects(DomainEvent event) {
        return switch (event.eventType()) {
            case "PayoutSent" -> List.of("creator-payout");
            case "RefundCompleted" -> List.of("customer-refund");
            default -> List.of();
        };
    }

    private String digest(ReplayState state) {
        String canonical = String.join("|", state.reservationStatus(), Long.toString(state.depositAmount()),
                String.valueOf(state.currency()), Boolean.toString(state.depositAuthorized()), state.refundStatus(),
                state.payoutStatus(), Long.toString(state.lastSequenceNumber()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
