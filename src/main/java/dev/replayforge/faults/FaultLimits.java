package dev.replayforge.faults;

public record FaultLimits(int maxDuplicates, long maxDelayMillis, int maxRetries, int maxEvents) {
    public FaultLimits {
        if (maxDuplicates < 0) throw new IllegalArgumentException("maxDuplicates must be zero or positive");
        if (maxDelayMillis < 0) throw new IllegalArgumentException("maxDelayMillis must be zero or positive");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be zero or positive");
        if (maxEvents < 1) throw new IllegalArgumentException("maxEvents must be positive");
    }
}
