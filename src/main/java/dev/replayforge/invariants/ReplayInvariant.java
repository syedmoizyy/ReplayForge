package dev.replayforge.invariants;

import java.util.Optional;

public interface ReplayInvariant {
    String id();
    String version();
    InvariantSeverity severity();
    Optional<InvariantViolation> evaluate(InvariantContext context);
}
