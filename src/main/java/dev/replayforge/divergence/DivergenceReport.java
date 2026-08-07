package dev.replayforge.divergence;

import dev.replayforge.invariants.InvariantViolation;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

public record DivergenceReport(String schemaVersion, Integer firstDivergentOrder,
        List<TransitionDifference> transitionDifferences, Map<String, FieldDifference> finalStateDifferences,
        List<InvariantViolation> invariantViolations) {
    public DivergenceReport {
        transitionDifferences = List.copyOf(transitionDifferences);
        finalStateDifferences = Collections.unmodifiableMap(new TreeMap<>(finalStateDifferences));
        invariantViolations = List.copyOf(invariantViolations);
    }
    public record TransitionDifference(long order, String category, String field, Object baseline, Object replay) {}
    public record FieldDifference(Object baseline, Object replay) {}
}
