package dev.replayforge.replay;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReplayDecision(long order, UUID sourceEventId, UUID replayEventId, Type type,
        Instant logicalTime, Map<String, String> detail) {
    public enum Type { CHECKPOINT_BASELINE_APPLIED, EVENT_REPLAYED }
    public ReplayDecision { detail = java.util.Collections.unmodifiableSortedMap(new java.util.TreeMap<>(detail)); }
}
