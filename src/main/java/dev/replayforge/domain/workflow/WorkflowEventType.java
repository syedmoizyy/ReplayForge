package dev.replayforge.domain.workflow;

import java.util.Set;

public final class WorkflowEventType {
    public static final String RESERVATION_CREATED = "ReservationCreated";
    public static final String DEPOSIT_AUTHORIZED = "DepositAuthorized";
    public static final String RESERVATION_CONFIRMED = "ReservationConfirmed";
    public static final String EVENT_CANCELLED = "EventCancelled";
    public static final String REFUND_REQUESTED = "RefundRequested";
    public static final String REFUND_COMPLETED = "RefundCompleted";
    public static final String PAYOUT_SCHEDULED = "PayoutScheduled";
    public static final String PAYOUT_SENT = "PayoutSent";
    public static final Set<String> ALL = Set.of(RESERVATION_CREATED, DEPOSIT_AUTHORIZED, RESERVATION_CONFIRMED,
            EVENT_CANCELLED, REFUND_REQUESTED, REFUND_COMPLETED, PAYOUT_SCHEDULED, PAYOUT_SENT);
    private WorkflowEventType() {}
}
