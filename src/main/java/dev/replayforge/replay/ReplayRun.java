package dev.replayforge.replay;

import java.time.Instant;
import java.util.UUID;

public record ReplayRun(UUID replayId, UUID sourceCorrelationId, long checkpoint, long seed, ClockMode clockMode,
        Status status, Instant createdAt, Instant startedAt, Instant completedAt, ReplayOutputSummary outputSummary,
        ReplayState finalState, String errorMessage) {
    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }
    public enum ClockMode { FIXED_EPOCH, SOURCE_RELATIVE }
}
