package dev.replayforge.invariants;

import static dev.replayforge.domain.workflow.WorkflowEventType.*;
import dev.replayforge.domain.event.DomainEvent;
import java.util.*;

final class StandardReplayInvariants {
    private StandardReplayInvariants() {}

    private abstract static class Rule implements ReplayInvariant {
        private final String id; private final InvariantSeverity severity;
        Rule(String id, InvariantSeverity severity) { this.id = id; this.severity = severity; }
        public String id() { return id; } public String version() { return "1"; }
        public InvariantSeverity severity() { return severity; }
        Optional<InvariantViolation> violation(InvariantContext c, List<UUID> ids, String expected, String actual) {
            return Optional.of(new InvariantViolation(id(), version(), ids, c.after(), expected, actual, severity(), c.eventPosition()));
        }
    }

    static final class NoPayoutAfterRefund extends Rule {
        NoPayoutAfterRefund() { super("no-payout-after-refund", InvariantSeverity.HARD); }
        public Optional<InvariantViolation> evaluate(InvariantContext c) {
            if (c.replayComplete()) return Optional.empty();
            if (PAYOUT_SENT.equals(c.event().eventType()) && "COMPLETED".equals(c.before().refundStatus())) {
                DomainEvent refund = last(c.eventsSeen(), REFUND_COMPLETED);
                return violation(c, ids(refund, c.event()), "no payout is sent after a completed refund", "PayoutSent followed RefundCompleted");
            }
            return Optional.empty();
        }
    }

    static final class AtMostOnceFinancialSideEffects extends Rule {
        AtMostOnceFinancialSideEffects() { super("at-most-once-financial-side-effects", InvariantSeverity.HARD); }
        public Optional<InvariantViolation> evaluate(InvariantContext c) {
            if (c.replayComplete()) return Optional.empty();
            String type = c.event().eventType();
            if (!Set.of(PAYOUT_SENT, REFUND_COMPLETED).contains(type)) return Optional.empty();
            List<DomainEvent> same = c.eventsSeen().stream().filter(e -> type.equals(e.eventType())).toList();
            if (same.size() > 1) return violation(c, same.stream().map(DomainEvent::eventId).toList(),
                    "at most one " + type + " financial side effect", same.size() + " " + type + " events observed");
            return Optional.empty();
        }
    }

    static final class ValidStateTransitions extends Rule {
        ValidStateTransitions() { super("valid-state-transitions", InvariantSeverity.HARD); }
        public Optional<InvariantViolation> evaluate(InvariantContext c) {
            if (c.replayComplete()) return Optional.empty();
            String type = c.event().eventType(); String before = c.before().reservationStatus();
            boolean valid = switch (type) {
                case RESERVATION_CREATED -> "NONE".equals(before);
                case DEPOSIT_AUTHORIZED -> "CREATED".equals(before);
                case RESERVATION_CONFIRMED -> "CREATED".equals(before) && c.before().depositAuthorized();
                case EVENT_CANCELLED -> Set.of("CREATED", "CONFIRMED").contains(before);
                case REFUND_REQUESTED -> "CANCELLED".equals(before) && c.before().depositAuthorized();
                case REFUND_COMPLETED -> "REQUESTED".equals(c.before().refundStatus());
                case PAYOUT_SCHEDULED -> "CONFIRMED".equals(before) && !"COMPLETED".equals(c.before().refundStatus());
                case PAYOUT_SENT -> "SCHEDULED".equals(c.before().payoutStatus()) && !"COMPLETED".equals(c.before().refundStatus());
                default -> true;
            };
            return valid ? Optional.empty() : violation(c, List.of(c.event().eventId()),
                    "event is permitted from the prior workflow state", type + " from reservation=" + before
                            + ", refund=" + c.before().refundStatus() + ", payout=" + c.before().payoutStatus());
        }
    }

    static final class ExactlyOneTerminalRefundAfterCancellation extends Rule {
        ExactlyOneTerminalRefundAfterCancellation() { super("exactly-one-terminal-refund-after-cancellation", InvariantSeverity.HARD); }
        public Optional<InvariantViolation> evaluate(InvariantContext c) {
            if (!c.replayComplete() || c.eventsSeen().stream().noneMatch(e -> EVENT_CANCELLED.equals(e.eventType()))) return Optional.empty();
            List<DomainEvent> terminal = c.eventsSeen().stream().filter(e -> REFUND_COMPLETED.equals(e.eventType())).toList();
            if (terminal.size() == 1) return Optional.empty();
            List<UUID> related = new ArrayList<>(); related.add(last(c.eventsSeen(), EVENT_CANCELLED).eventId());
            terminal.forEach(e -> related.add(e.eventId()));
            return violation(c, related, "exactly one RefundCompleted after cancellation", terminal.size() + " terminal refunds observed");
        }
    }

    static final class MonotonicEventSequence extends Rule {
        MonotonicEventSequence() { super("monotonic-event-sequence", InvariantSeverity.WARNING); }
        public Optional<InvariantViolation> evaluate(InvariantContext c) {
            if (c.replayComplete()) return Optional.empty();
            if (c.eventsSeen().size() < 2) return Optional.empty();
            DomainEvent prior = c.eventsSeen().get(c.eventsSeen().size() - 2);
            return c.event().sequenceNumber() > prior.sequenceNumber() ? Optional.empty()
                    : violation(c, ids(prior, c.event()), "sequence numbers strictly increase",
                            prior.sequenceNumber() + " followed by " + c.event().sequenceNumber());
        }
    }

    private static DomainEvent last(List<DomainEvent> events, String type) {
        for (int i = events.size() - 1; i >= 0; i--) if (type.equals(events.get(i).eventType())) return events.get(i);
        throw new IllegalStateException("Missing related event " + type);
    }
    private static List<UUID> ids(DomainEvent... events) { return Arrays.stream(events).map(DomainEvent::eventId).toList(); }
}
