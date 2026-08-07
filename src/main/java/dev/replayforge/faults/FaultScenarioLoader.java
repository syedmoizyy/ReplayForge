package dev.replayforge.faults;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class FaultScenarioLoader {
    private final ObjectMapper mapper;
    public FaultScenarioLoader(ObjectMapper mapper) { this.mapper = mapper; }
    public FaultScenario readJson(String json) {
        try { return mapper.readValue(json, FaultScenario.class); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid fault scenario JSON", exception); }
    }
}
