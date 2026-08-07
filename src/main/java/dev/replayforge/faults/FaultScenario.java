package dev.replayforge.faults;

import java.util.List;
import java.util.Objects;

public record FaultScenario(int schemaVersion, String name, long seed, FaultLimits limits, List<FaultSpec> faults) {
    public FaultScenario {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported fault scenario schemaVersion: " + schemaVersion);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("scenario name is required");
        Objects.requireNonNull(limits, "limits are required");
        faults = List.copyOf(Objects.requireNonNull(faults, "faults are required"));
        if (faults.isEmpty()) throw new IllegalArgumentException("at least one fault is required");
        long uniqueIds = faults.stream().map(FaultSpec::id).distinct().count();
        if (uniqueIds != faults.size()) throw new IllegalArgumentException("fault ids must be unique");
    }
}
