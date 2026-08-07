package dev.replayforge.replay;

import dev.replayforge.divergence.DivergenceReport;
import dev.replayforge.divergence.TraceTransition;
import dev.replayforge.invariants.InvariantViolation;
import java.util.List;

public record ReplayExecution(List<ReplayedEvent> events, List<ReplayDecision> decisions,
        ReplayState finalState, ReplayOutputSummary summary, List<InvariantViolation> violations,
        List<TraceTransition> transitions, DivergenceReport divergenceReport,
        String divergenceReportJson, String divergenceReportMarkdown) {
    public ReplayExecution {
        events = List.copyOf(events); decisions = List.copyOf(decisions);
        violations = List.copyOf(violations); transitions = List.copyOf(transitions);
    }
}
