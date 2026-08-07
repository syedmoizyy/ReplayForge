package dev.replayforge.replay;

public record ReplayOutputSummary(int sourceEventCount, int baselineEventCount, int replayedEventCount,
        int decisionCount, String stateDigest) {}
