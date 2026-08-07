package dev.replayforge.sampleworkload;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.workflow.ReservationProjection;
import dev.replayforge.domain.workflow.WorkflowEventType;
import dev.replayforge.eventstore.AppendResult;
import dev.replayforge.eventstore.EventStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowEngine {
    private final EventStore events;
    private final ReservationProjectionRepository projections;
    private final WorkflowOutbox outbox;
    private final Clock clock;

    public WorkflowEngine(EventStore events, ReservationProjectionRepository projections, WorkflowOutbox outbox) {
        this(events, projections, outbox, Clock.systemUTC());
    }
    WorkflowEngine(EventStore events, ReservationProjectionRepository projections, WorkflowOutbox outbox, Clock clock) {
        this.events = events; this.projections = projections; this.outbox = outbox; this.clock = clock;
    }

    @Transactional
    public DomainEvent start(long amount, String currency, boolean autoPayout, String idempotencyKey) {
        if (amount <= 0) throw new IllegalArgumentException("depositAmount must be positive");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        UUID reservation = UUID.randomUUID(); UUID correlation = UUID.randomUUID(); Instant now = clock.instant();
        DomainEvent created = new DomainEvent(UUID.randomUUID(), WorkflowEventType.RESERVATION_CREATED, 1, reservation,
                correlation, null, idempotencyKey, 1, now, now,
                JsonNodeFactory.instance.objectNode().put("depositAmount", amount).put("currency", currency).put("autoPayout", autoPayout), Map.of());
        events.append(created);
        projections.create(reservation, correlation, amount, currency, autoPayout, now);
        outbox.enqueue(created);
        return created;
    }

    @Transactional
    public DomainEvent cancel(UUID reservationId, String idempotencyKey) {
        ReservationProjection state = state(reservationId);
        if (state.status() != ReservationProjection.Status.CONFIRMED)
            throw new WorkflowTransitionException("Only a confirmed reservation can be cancelled");
        if (state.payoutStatus() != ReservationProjection.PayoutStatus.NONE)
            throw new WorkflowTransitionException("A reservation with payout activity cannot be cancelled safely");
        List<DomainEvent> stream = events.findByAggregateId(reservationId);
        DomainEvent cause = stream.isEmpty() ? null : stream.getLast();
        return appendAndProject(next(state, WorkflowEventType.EVENT_CANCELLED, cause, idempotencyKey), state,
                "status = 'CANCELLED'");
    }

    @Transactional
    public DomainEvent react(String consumer, DomainEvent input) {
        if (!claim(consumer, input.eventId())) return null;
        ReservationProjection state = state(input.aggregateId());
        return switch (consumer + ":" + input.eventType()) {
            case "payment:ReservationCreated" -> state.status() == ReservationProjection.Status.CREATED && !state.depositAuthorized()
                    ? appendAndProject(next(state, WorkflowEventType.DEPOSIT_AUTHORIZED, input, key(consumer,input)), state, "deposit_authorized = true") : null;
            case "reservation:DepositAuthorized" -> state.status() == ReservationProjection.Status.CREATED && state.depositAuthorized()
                    ? appendAndProject(next(state, WorkflowEventType.RESERVATION_CONFIRMED, input, key(consumer,input)), state, "status = 'CONFIRMED'") : null;
            case "refund:EventCancelled" -> state.status() == ReservationProjection.Status.CANCELLED && state.refundStatus() == ReservationProjection.RefundStatus.NONE
                    ? appendAndProject(next(state, WorkflowEventType.REFUND_REQUESTED, input, key(consumer,input)), state, "refund_status = 'REQUESTED'") : null;
            case "refund:RefundRequested" -> state.status() == ReservationProjection.Status.CANCELLED && state.refundStatus() == ReservationProjection.RefundStatus.REQUESTED
                    ? appendAndProject(next(state, WorkflowEventType.REFUND_COMPLETED, input, key(consumer,input)), state, "refund_status = 'COMPLETED'") : null;
            case "payout:ReservationConfirmed" -> state.autoPayout() && state.status() == ReservationProjection.Status.CONFIRMED
                    && state.refundStatus() == ReservationProjection.RefundStatus.NONE && state.payoutStatus() == ReservationProjection.PayoutStatus.NONE
                    ? appendAndProject(next(state, WorkflowEventType.PAYOUT_SCHEDULED, input, key(consumer,input)), state, "payout_status = 'SCHEDULED'") : null;
            case "payout:PayoutScheduled" -> state.status() == ReservationProjection.Status.CONFIRMED
                    && state.refundStatus() == ReservationProjection.RefundStatus.NONE && state.payoutStatus() == ReservationProjection.PayoutStatus.SCHEDULED
                    ? appendAndProject(next(state, WorkflowEventType.PAYOUT_SENT, input, key(consumer,input)), state, "payout_status = 'SENT'") : null;
            default -> null;
        };
    }

    public ReservationProjection state(UUID id) {
        return projections.find(id).orElseThrow(() -> new WorkflowTransitionException("Reservation not found: " + id));
    }

    private boolean claim(String consumer, UUID eventId) {
        return projections.claim(consumer, eventId);
    }

    private DomainEvent next(ReservationProjection state, String type, DomainEvent cause, String key) {
        Instant now = clock.instant();
        return new DomainEvent(UUID.randomUUID(), type, 1, state.reservationId(), state.correlationId(),
                cause == null ? null : cause.eventId(), key, state.lastSequenceNumber() + 1, now, now,
                JsonNodeFactory.instance.objectNode().put("depositAmount", state.depositAmount()).put("currency", state.currency()),
                Map.of("producer", "sample-workload"));
    }

    private DomainEvent appendAndProject(DomainEvent event, ReservationProjection prior, String assignments) {
        AppendResult result = events.append(event);
        if (result.status() == AppendResult.Status.IDEMPOTENT_REPLAY) return result.event();
        if (!projections.apply(prior.reservationId(), prior.lastSequenceNumber(), event.sequenceNumber(), assignments))
            throw new WorkflowTransitionException("Projection changed concurrently for " + prior.reservationId());
        outbox.enqueue(event);
        return event;
    }

    private String key(String consumer, DomainEvent input) { return consumer + ":" + input.eventId(); }
}
