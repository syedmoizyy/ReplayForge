package dev.replayforge.replay;

import java.util.List;

public record ReplayExecution(List<ReplayedEvent> events, List<ReplayDecision> decisions,
        ReplayState finalState, ReplayOutputSummary summary) {
    public ReplayExecution { events = List.copyOf(events); decisions = List.copyOf(decisions); }
}
