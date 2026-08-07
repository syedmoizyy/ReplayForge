package dev.replayforge.replay;

import java.util.SplittableRandom;
import java.util.UUID;

public final class SeededDeterministicScheduler {
    private final SplittableRandom random;
    public SeededDeterministicScheduler(long seed) { random = new SplittableRandom(seed); }
    public UUID nextId() {
        return new UUID(random.nextLong(), random.nextLong());
    }
}
