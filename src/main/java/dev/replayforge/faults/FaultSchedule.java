package dev.replayforge.faults;

import dev.replayforge.domain.event.DomainEvent;
import java.util.List;
import java.util.UUID;

public record FaultSchedule(List<ScheduledEvent> events, List<FaultDecision> decisions, List<ExecutionDirective> directives) {
    public FaultSchedule {
        events = List.copyOf(events); decisions = List.copyOf(decisions); directives = List.copyOf(directives);
    }
    public record ScheduledEvent(DomainEvent event, int attempt, long logicalDelayMillis, String provenance) {}
    public record FaultDecision(String faultId, FaultType type, UUID eventId, Outcome outcome, String rationale) {
        public enum Outcome { APPLIED, SKIPPED }
    }
    public record ExecutionDirective(String faultId, FaultType type, UUID eventId, Boundary boundary, String rationale) {
        public enum Boundary { BEFORE_SIDE_EFFECT, AFTER_SIDE_EFFECT, DEPENDENCY_CALL }
    }
}
