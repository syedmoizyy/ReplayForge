package dev.replayforge.invariants;

import dev.replayforge.replay.ReplayState;
import java.util.List;
import java.util.UUID;

public record InvariantViolation(String ruleId, String version, List<UUID> relatedEventIds,
        ReplayState stateSnapshot, String expectedCondition, String actualCondition,
        InvariantSeverity severity, long eventPosition) {
    public InvariantViolation { relatedEventIds = List.copyOf(relatedEventIds); }
}
