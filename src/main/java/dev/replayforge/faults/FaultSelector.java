package dev.replayforge.faults;

import dev.replayforge.domain.event.DomainEvent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public record FaultSelector(String eventType, UUID aggregateId, Long sequenceFrom, Long sequenceTo,
        Integer attemptFrom, Integer attemptTo, Double probability) {
    public FaultSelector {
        if (sequenceFrom != null && sequenceFrom < 1) throw new IllegalArgumentException("sequenceFrom must be positive");
        if (sequenceTo != null && sequenceTo < 1) throw new IllegalArgumentException("sequenceTo must be positive");
        if (sequenceFrom != null && sequenceTo != null && sequenceFrom > sequenceTo)
            throw new IllegalArgumentException("sequenceFrom must not exceed sequenceTo");
        if (attemptFrom != null && attemptFrom < 1) throw new IllegalArgumentException("attemptFrom must be positive");
        if (attemptTo != null && attemptTo < 1) throw new IllegalArgumentException("attemptTo must be positive");
        if (attemptFrom != null && attemptTo != null && attemptFrom > attemptTo)
            throw new IllegalArgumentException("attemptFrom must not exceed attemptTo");
        if (probability != null && (probability < 0 || probability > 1 || !Double.isFinite(probability)))
            throw new IllegalArgumentException("probability must be between 0 and 1");
    }

    Match match(DomainEvent event, int attempt, long seed, String faultId) {
        if (eventType != null && !eventType.equals(event.eventType())) return new Match(false, "eventType did not match");
        if (aggregateId != null && !aggregateId.equals(event.aggregateId())) return new Match(false, "aggregateId did not match");
        if (sequenceFrom != null && event.sequenceNumber() < sequenceFrom) return new Match(false, "sequence below range");
        if (sequenceTo != null && event.sequenceNumber() > sequenceTo) return new Match(false, "sequence above range");
        if (attemptFrom != null && attempt < attemptFrom) return new Match(false, "attempt below range");
        if (attemptTo != null && attempt > attemptTo) return new Match(false, "attempt above range");
        if (probability != null) {
            double sample = sample(seed, faultId, event.eventId(), attempt);
            if (sample >= probability) return new Match(false, "seeded probability sample " + sample + " was not below " + probability);
            return new Match(true, "selector matched; seeded probability sample " + sample + " was below " + probability);
        }
        return new Match(true, "selector matched");
    }

    private static double sample(long seed, String faultId, UUID eventId, int attempt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(seed).array());
            digest.update(faultId.getBytes(StandardCharsets.UTF_8));
            digest.update(eventId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(attempt).array());
            long value = ByteBuffer.wrap(digest.digest()).getLong() >>> 11;
            return value * 0x1.0p-53;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    record Match(boolean matched, String rationale) {}
}
