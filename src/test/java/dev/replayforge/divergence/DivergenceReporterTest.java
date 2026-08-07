package dev.replayforge.divergence;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.replayforge.replay.ReplayState;
import java.util.*;
import org.junit.jupiter.api.Test;

class DivergenceReporterTest {
    private final DivergenceReporter reporter = new DivergenceReporter(new ObjectMapper());

    @Test void reportsPayloadSideEffectAndFinalStateDiffs() {
        UUID id = new UUID(0, 1);
        TraceTransition baseline = new TraceTransition(1, id, "PayoutSent",
                JsonNodeFactory.instance.objectNode().put("amount", 100), List.of("creator-payout"), state("SENT", 1));
        TraceTransition replay = new TraceTransition(1, id, "PayoutSent",
                JsonNodeFactory.instance.objectNode().put("amount", 90), List.of(), state("SCHEDULED", 1));
        DivergenceReport report = reporter.compare(List.of(baseline), List.of(replay), baseline.state(), replay.state());
        assertThat(report.firstDivergentOrder()).isEqualTo(1);
        assertThat(report.transitionDifferences()).extracting(DivergenceReport.TransitionDifference::category)
                .containsExactly("payload", "side-effects");
        assertThat(report.finalStateDifferences()).containsKey("payoutStatus");
    }

    @Test void jsonAndMarkdownReportsAreStable() {
        DivergenceReport report = reporter.compare(List.of(), List.of(), state("NONE", 0), state("NONE", 0));
        assertThat(reporter.json(report)).isEqualTo(reporter.json(report));
        assertThat(reporter.markdown(report)).isEqualTo("""
                # Replay divergence report

                No transition divergence detected.

                ## Final state

                No final aggregate state differences.

                ## Invariant violations

                No invariant violations.
                """);
    }
    private ReplayState state(String payout, long sequence) {
        return new ReplayState("CONFIRMED", 100, "USD", true, "NONE", payout, sequence);
    }
}
