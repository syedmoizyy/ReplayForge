package dev.replayforge.replay;

import dev.replayforge.domain.event.DomainEvent;
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
import org.springframework.stereotype.Component;

@Component
public final class DeterministicReplayEngine {
    private static final Instant FIXED_EPOCH = Instant.parse("2000-01-01T00:00:00Z");
    private final ReservationReplayReducer reducer = new ReservationReplayReducer();

    public ReplayExecution execute(List<DomainEvent> source, long checkpoint, long seed, ReplayRun.ClockMode clockMode) {
        if (source.isEmpty()) throw new ReplayValidationException("Source trace is empty");
        if (checkpoint < 0) throw new ReplayValidationException("checkpoint must be zero or positive");
        long maximumSequence = source.stream().mapToLong(DomainEvent::sequenceNumber).max().orElseThrow();
        if (checkpoint > maximumSequence) throw new ReplayValidationException(
                "checkpoint " + checkpoint + " exceeds source maximum sequence " + maximumSequence);

        SeededDeterministicScheduler scheduler = new SeededDeterministicScheduler(seed);
        Instant initial = clockMode == ReplayRun.ClockMode.SOURCE_RELATIVE ? source.getFirst().occurredAt() : FIXED_EPOCH;
        VirtualClock clock = new VirtualClock(initial);
        Map<UUID, UUID> eventIds = new HashMap<>();
        Map<UUID, UUID> aggregateIds = new HashMap<>();
        UUID replayCorrelationId = scheduler.nextId();
        List<ReplayedEvent> output = new ArrayList<>();
        List<ReplayDecision> decisions = new ArrayList<>();
        ReplayState state = ReplayState.empty();
        int baselineCount = 0;

        for (int index = 0; index < source.size(); index++) {
            DomainEvent original = source.get(index);
            Instant logicalTime = index == 0 ? clock.instant() : clock.advance(Duration.ofMillis(1));
            UUID replayEventId = scheduler.nextId();
            eventIds.put(original.eventId(), replayEventId);
            UUID replayAggregateId = aggregateIds.computeIfAbsent(original.aggregateId(), ignored -> scheduler.nextId());
            state = reducer.apply(state, original);
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

        ReplayOutputSummary summary = new ReplayOutputSummary(source.size(), baselineCount, output.size(),
                decisions.size(), digest(state));
        return new ReplayExecution(output, decisions, state, summary);
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
