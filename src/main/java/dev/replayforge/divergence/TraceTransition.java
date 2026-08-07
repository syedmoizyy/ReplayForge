package dev.replayforge.divergence;

import com.fasterxml.jackson.databind.JsonNode;
import dev.replayforge.replay.ReplayState;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TraceTransition(long order, UUID eventId, String eventType, JsonNode payload,
        List<String> sideEffects, ReplayState state) {
    public TraceTransition { sideEffects = List.copyOf(sideEffects); }
}
