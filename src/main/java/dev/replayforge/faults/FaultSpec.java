package dev.replayforge.faults;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Objects;

public record FaultSpec(String id, FaultType type, FaultSelector selector, Map<String, JsonNode> parameters) {
    public FaultSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("fault id is required");
        Objects.requireNonNull(type, "fault type is required");
        selector = selector == null ? new FaultSelector(null, null, null, null, null, null, null) : selector;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
