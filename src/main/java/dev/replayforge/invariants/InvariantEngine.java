package dev.replayforge.invariants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered, pluggable rule registry. Rule order is report order. */
public final class InvariantEngine {
    private final Map<String, ReplayInvariant> rules = new LinkedHashMap<>();

    public InvariantEngine register(ReplayInvariant rule) {
        if (rules.putIfAbsent(rule.id(), rule) != null)
            throw new IllegalArgumentException("Invariant already registered: " + rule.id());
        return this;
    }

    public List<ReplayInvariant> rules() { return List.copyOf(rules.values()); }

    public List<InvariantViolation> evaluate(InvariantContext context) {
        List<InvariantViolation> result = new ArrayList<>();
        rules.values().forEach(rule -> rule.evaluate(context).ifPresent(result::add));
        return List.copyOf(result);
    }

    public static InvariantEngine standard() {
        return new InvariantEngine().register(new StandardReplayInvariants.NoPayoutAfterRefund())
                .register(new StandardReplayInvariants.AtMostOnceFinancialSideEffects())
                .register(new StandardReplayInvariants.ValidStateTransitions())
                .register(new StandardReplayInvariants.ExactlyOneTerminalRefundAfterCancellation())
                .register(new StandardReplayInvariants.MonotonicEventSequence());
    }
}
