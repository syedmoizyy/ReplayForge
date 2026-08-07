package dev.replayforge.replay;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.workflow.WorkflowEventType;

public final class ReservationReplayReducer {
    public ReplayState apply(ReplayState state, DomainEvent event) {
        long amount = event.payload().path("depositAmount").asLong(state.depositAmount());
        String currency = event.payload().hasNonNull("currency") ? event.payload().get("currency").asText() : state.currency();
        return switch (event.eventType()) {
            case WorkflowEventType.RESERVATION_CREATED -> new ReplayState("CREATED", amount, currency, false, "NONE", "NONE", event.sequenceNumber());
            case WorkflowEventType.DEPOSIT_AUTHORIZED -> new ReplayState(state.reservationStatus(), amount, currency, true, state.refundStatus(), state.payoutStatus(), event.sequenceNumber());
            case WorkflowEventType.RESERVATION_CONFIRMED -> new ReplayState("CONFIRMED", amount, currency, state.depositAuthorized(), state.refundStatus(), state.payoutStatus(), event.sequenceNumber());
            case WorkflowEventType.EVENT_CANCELLED -> new ReplayState("CANCELLED", amount, currency, state.depositAuthorized(), state.refundStatus(), state.payoutStatus(), event.sequenceNumber());
            case WorkflowEventType.REFUND_REQUESTED -> new ReplayState(state.reservationStatus(), amount, currency, state.depositAuthorized(), "REQUESTED", state.payoutStatus(), event.sequenceNumber());
            case WorkflowEventType.REFUND_COMPLETED -> new ReplayState(state.reservationStatus(), amount, currency, state.depositAuthorized(), "COMPLETED", state.payoutStatus(), event.sequenceNumber());
            case WorkflowEventType.PAYOUT_SCHEDULED -> new ReplayState(state.reservationStatus(), amount, currency, state.depositAuthorized(), state.refundStatus(), "SCHEDULED", event.sequenceNumber());
            case WorkflowEventType.PAYOUT_SENT -> new ReplayState(state.reservationStatus(), amount, currency, state.depositAuthorized(), state.refundStatus(), "SENT", event.sequenceNumber());
            default -> new ReplayState(state.reservationStatus(), state.depositAmount(), state.currency(), state.depositAuthorized(),
                    state.refundStatus(), state.payoutStatus(), event.sequenceNumber());
        };
    }
}
