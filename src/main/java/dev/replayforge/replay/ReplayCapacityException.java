package dev.replayforge.replay;

public final class ReplayCapacityException extends RuntimeException {
    private final int retryAfterSeconds;

    public ReplayCapacityException(int retryAfterSeconds) {
        super("Replay capacity is exhausted; retry after " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() { return retryAfterSeconds; }
}
