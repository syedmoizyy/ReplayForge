package dev.replayforge.domain.workflow;

import java.time.Instant;
import java.util.UUID;

public record ReservationProjection(UUID reservationId, UUID correlationId, Status status, long depositAmount,
        String currency, boolean autoPayout, boolean depositAuthorized, RefundStatus refundStatus, PayoutStatus payoutStatus,
        long lastSequenceNumber, Instant updatedAt) {
    public enum Status { CREATED, CONFIRMED, CANCELLED }
    public enum RefundStatus { NONE, REQUESTED, COMPLETED }
    public enum PayoutStatus { NONE, SCHEDULED, SENT }
}
