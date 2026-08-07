package dev.replayforge.invariants;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.replay.ReplayState;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvariantEngineTest {
    @Test void registersRulesInOrderAndRejectsDuplicateIds() {
        ReplayInvariant first = rule("first"), second = rule("second");
        InvariantEngine engine = new InvariantEngine().register(first).register(second);
        assertThat(engine.rules()).extracting(ReplayInvariant::id).containsExactly("first", "second");
        assertThatThrownBy(() -> engine.register(rule("first"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void localizesPayoutAfterRefundToBothEventsAndSnapshot() {
        DomainEvent refund = event(1, 1, "RefundCompleted"), payout = event(2, 2, "PayoutSent");
        ReplayState before = new ReplayState("CANCELLED", 100, "USD", true, "COMPLETED", "SCHEDULED", 1);
        ReplayState after = new ReplayState("CANCELLED", 100, "USD", true, "COMPLETED", "SENT", 2);
        List<InvariantViolation> result = InvariantEngine.standard().evaluate(
                new InvariantContext(payout, before, after, List.of(refund, payout), 2, false));
        assertThat(result).filteredOn(v -> v.ruleId().equals("no-payout-after-refund")).singleElement().satisfies(v -> {
            assertThat(v.relatedEventIds()).containsExactly(refund.eventId(), payout.eventId());
            assertThat(v.stateSnapshot()).isEqualTo(after);
            assertThat(v.severity()).isEqualTo(InvariantSeverity.HARD);
        });
    }

    private ReplayInvariant rule(String id) {
        return new ReplayInvariant() {
            public String id() { return id; } public String version() { return "1"; }
            public InvariantSeverity severity() { return InvariantSeverity.WARNING; }
            public Optional<InvariantViolation> evaluate(InvariantContext context) { return Optional.empty(); }
        };
    }
    private DomainEvent event(long id, long sequence, String type) {
        UUID value = new UUID(0, id); Instant time = Instant.parse("2026-01-01T00:00:00Z");
        return new DomainEvent(value, type, 1, new UUID(0, 20), new UUID(0, 30), null, "key-" + id,
                sequence, time, time, JsonNodeFactory.instance.objectNode().put("depositAmount", 100), Map.of());
    }
}
