package dev.replayforge.replay;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

public final class VirtualClock extends Clock {
    private Instant current;
    public VirtualClock(Instant initial) { current = Objects.requireNonNull(initial); }
    public Instant advance(Duration duration) {
        if (duration.isNegative()) throw new IllegalArgumentException("Virtual time cannot move backwards");
        current = current.plus(duration); return current;
    }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("Replay clock is fixed to UTC");
        return this;
    }
    @Override public Instant instant() { return current; }
}
