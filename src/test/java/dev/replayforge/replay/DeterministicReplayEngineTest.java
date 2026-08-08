package dev.replayforge.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.replayforge.domain.event.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;

class DeterministicReplayEngineTest {
    private static final UUID AGGREGATE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID CORRELATION = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private final DeterministicReplayEngine engine = new DeterministicReplayEngine();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test void sameSeedProducesIdenticalOrderStateAndReportOutput() throws Exception {
        List<DomainEvent> source = sourceTrace();
        ReplayExecution first = engine.execute(source, 0, 8675309L, ReplayRun.ClockMode.FIXED_EPOCH);
        ReplayExecution second = engine.execute(source, 0, 8675309L, ReplayRun.ClockMode.FIXED_EPOCH);

        assertThat(second.events()).isEqualTo(first.events());
        assertThat(second.finalState()).isEqualTo(first.finalState());
        assertThat(second.decisions()).isEqualTo(first.decisions());
        assertThat(second.summary()).isEqualTo(first.summary());
        assertThat(mapper.writeValueAsString(second)).isEqualTo(mapper.writeValueAsString(first));
    }

    @Test void checkpointRebuildsBaselineButOnlyEmitsEventsAfterSequence() {
        ReplayExecution replay = engine.execute(sourceTrace(), 2, 42L, ReplayRun.ClockMode.SOURCE_RELATIVE);

        assertThat(replay.events()).extracting(event -> event.event().eventType())
                .containsExactly("ReservationConfirmed", "PayoutScheduled", "PayoutSent");
        assertThat(replay.decisions()).extracting(ReplayDecision::type)
                .startsWith(ReplayDecision.Type.CHECKPOINT_BASELINE_APPLIED, ReplayDecision.Type.CHECKPOINT_BASELINE_APPLIED)
                .endsWith(ReplayDecision.Type.EVENT_REPLAYED);
        assertThat(replay.finalState().payoutStatus()).isEqualTo("SENT");
        assertThat(replay.summary().baselineEventCount()).isEqualTo(2);
    }

    @Test void virtualOrderingAdvancesByLogicalTicksWithoutSleeping() {
        ReplayExecution replay = engine.execute(sourceTrace(), 0, 1L, ReplayRun.ClockMode.FIXED_EPOCH);
        assertThat(replay.events()).extracting(event -> event.event().occurredAt()).containsExactly(
                Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2000-01-01T00:00:00.001Z"),
                Instant.parse("2000-01-01T00:00:00.002Z"), Instant.parse("2000-01-01T00:00:00.003Z"),
                Instant.parse("2000-01-01T00:00:00.004Z"));
    }

    @Test void sharedEngineIsSafeAcrossConcurrentReplayRuns() throws Exception {
        List<DomainEvent> source = sourceTrace();
        ReplayExecution expected = engine.execute(source, 0, 77L, ReplayRun.ClockMode.FIXED_EPOCH);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(ignored -> executor.submit(() -> engine.execute(source, 0, 77L, ReplayRun.ClockMode.FIXED_EPOCH)))
                    .toList();
            for (var task : tasks) {
                ReplayExecution actual = task.get();
                assertThat(actual.events()).isEqualTo(expected.events());
                assertThat(actual.summary()).isEqualTo(expected.summary());
                assertThat(actual.divergenceReportJson()).isEqualTo(expected.divergenceReportJson());
            }
        }
    }

    private List<DomainEvent> sourceTrace() {
        UUID first = id(1); UUID second = id(2); UUID third = id(3); UUID fourth = id(4);
        return List.of(event(first, null, 1, "ReservationCreated"), event(second, first, 2, "DepositAuthorized"),
                event(third, second, 3, "ReservationConfirmed"), event(fourth, third, 4, "PayoutScheduled"),
                event(id(5), fourth, 5, "PayoutSent"));
    }

    private DomainEvent event(UUID eventId, UUID causationId, long sequence, String type) {
        Instant time = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequence);
        return new DomainEvent(eventId, type, 1, AGGREGATE, CORRELATION, causationId, "source-" + sequence,
                sequence, time, time, JsonNodeFactory.instance.objectNode().put("depositAmount", 2500).put("currency", "USD"),
                Map.of("fixture", "determinism"));
    }
    private UUID id(long value) { return new UUID(0, value); }
}
