package dev.replayforge.replay;

public record ReplayState(String reservationStatus, long depositAmount, String currency, boolean depositAuthorized,
        String refundStatus, String payoutStatus, long lastSequenceNumber) {
    public static ReplayState empty() { return new ReplayState("NONE", 0, null, false, "NONE", "NONE", 0); }
}
