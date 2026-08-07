package dev.replayforge.faults;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.faults.FaultSchedule.ExecutionDirective;
import dev.replayforge.faults.FaultSchedule.FaultDecision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FaultScheduleCompilerTest {
    private static final UUID AGGREGATE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final FaultLimits LIMITS = new FaultLimits(3, 10_000, 4, 20);
    private final FaultScheduleCompiler compiler = new FaultScheduleCompiler();

    @Test void duplicateIsReproducibleAndGetsDeterministicIdentity() {
        FaultScenario scenario = scenario(spec("copy", FaultType.DUPLICATE, selector("PaymentAuthorized"), "count", 2));
        assertReproducible(scenario);
        FaultSchedule result = compiler.compile(scenario, source());
        assertThat(result.events()).hasSize(5);
        assertThat(result.events()).filteredOn(item -> item.event().eventType().equals("PaymentAuthorized")).hasSize(3);
        assertThat(result.decisions()).anyMatch(item -> item.outcome() == FaultDecision.Outcome.APPLIED);
    }

    @Test void dropIsReproducible() {
        FaultScenario scenario = scenario(spec("drop", FaultType.DROP, selector("RefundRequested")));
        assertReproducible(scenario);
        assertThat(compiler.compile(scenario, source()).events()).extracting(item -> item.event().eventType())
                .doesNotContain("RefundRequested");
    }

    @Test void delayIsLogicalAndReproducible() {
        FaultScenario scenario = scenario(spec("delay", FaultType.DELAY, selector("RefundRequested"), "durationMillis", 5000));
        assertReproducible(scenario);
        assertThat(compiler.compile(scenario, source()).events().getLast().logicalDelayMillis()).isEqualTo(5000);
    }

    @Test void reorderIsReproducible() {
        FaultScenario scenario = scenario(spec("first", FaultType.REORDER, selector("RefundRequested"), "position", "FIRST"));
        assertReproducible(scenario);
        assertThat(compiler.compile(scenario, source()).events().getFirst().event().eventType()).isEqualTo("RefundRequested");
    }

    @Test void retryStormIsReproducibleAndNumbersAttempts() {
        FaultScenario scenario = scenario(spec("retry", FaultType.RETRY_STORM, selector("PaymentAuthorized"), "retries", 3));
        assertReproducible(scenario);
        assertThat(compiler.compile(scenario, source()).events()).filteredOn(item -> item.event().eventType().equals("PaymentAuthorized"))
                .extracting(item -> item.attempt()).containsExactly(1, 2, 3, 4);
    }

    @Test void crashAndTimeoutDirectivesAreReproducibleAndSideEffectFree() {
        FaultScenario scenario = scenario(
                spec("crash", FaultType.WORKER_CRASH, selector("PaymentAuthorized"), "boundary", "AFTER_SIDE_EFFECT"),
                spec("timeout", FaultType.DEPENDENCY_TIMEOUT, selector("RefundRequested")));
        assertReproducible(scenario);
        assertThat(compiler.compile(scenario, source()).directives()).extracting(ExecutionDirective::boundary)
                .containsExactly(ExecutionDirective.Boundary.AFTER_SIDE_EFFECT, ExecutionDirective.Boundary.DEPENDENCY_CALL);
    }

    @Test void malformedPayloadIsReproducibleAndIsolatedFromSource() {
        ObjectNodeBuilder patch = new ObjectNodeBuilder().put("schemaVersion", 999).putObject("payloadPatch", "broken", true);
        FaultScenario scenario = scenario(new FaultSpec("malformed", FaultType.MALFORMED_PAYLOAD, selector("PaymentAuthorized"), patch.values));
        assertReproducible(scenario);
        DomainEvent changed = compiler.compile(scenario, source()).events().get(1).event();
        assertThat(changed.schemaVersion()).isEqualTo(999);
        assertThat(changed.payload().get("broken").booleanValue()).isTrue();
        assertThat(source().get(1).payload().has("broken")).isFalse();
    }

    @Test void selectorsComposeAndProbabilityIsSeeded() {
        FaultSelector selector = new FaultSelector("PaymentAuthorized", AGGREGATE, 2L, 2L, 1, 1, .5);
        FaultScenario scenario = scenario(new FaultSpec("probability", FaultType.DROP, selector, Map.of()));
        FaultSchedule first = compiler.compile(scenario, source());
        FaultSchedule second = compiler.compile(scenario, source());
        assertThat(second).isEqualTo(first);
        assertThat(first.decisions()).allMatch(item -> !item.rationale().isBlank());
        assertThat(first.decisions()).anyMatch(item -> item.rationale().contains("seeded probability sample"));
    }

    @Test void recordsAppliedAndSkippedFaultsWithRationales() {
        FaultSchedule result = compiler.compile(scenario(spec("drop", FaultType.DROP, selector("PaymentAuthorized"))), source());
        assertThat(result.decisions()).extracting(FaultDecision::outcome)
                .contains(FaultDecision.Outcome.APPLIED, FaultDecision.Outcome.SKIPPED);
        assertThat(result.decisions()).allMatch(item -> !item.rationale().isBlank());
    }

    @Test void guardrailsRejectExcessiveDuplicatesDelaysRetriesSourceAndCompiledEvents() {
        assertRejected(spec("copies", FaultType.DUPLICATE, selector("PaymentAuthorized"), "count", 4), "maxDuplicates");
        assertRejected(spec("delay", FaultType.DELAY, selector("PaymentAuthorized"), "durationMillis", 10_001), "maxDelayMillis");
        assertRejected(spec("retry", FaultType.RETRY_STORM, selector("PaymentAuthorized"), "retries", 5), "maxRetries");
        FaultScenario cumulativeDelay = scenario(
                spec("delay-one", FaultType.DELAY, selector("PaymentAuthorized"), "durationMillis", 6000),
                spec("delay-two", FaultType.DELAY, selector("PaymentAuthorized"), "durationMillis", 6000));
        assertThatThrownBy(() -> compiler.compile(cumulativeDelay, source())).hasMessageContaining("cumulative logical delay");
        FaultScenario smallSource = new FaultScenario(1, "small", 42, new FaultLimits(3, 10000, 4, 2),
                List.of(spec("drop", FaultType.DROP, selector("none"))));
        assertThatThrownBy(() -> compiler.compile(smallSource, source())).hasMessageContaining("source has");
        FaultScenario compiledOverflow = new FaultScenario(1, "overflow", 42, new FaultLimits(3, 10000, 4, 4),
                List.of(spec("copy", FaultType.DUPLICATE, selector("PaymentAuthorized"), "count", 2)));
        assertThatThrownBy(() -> compiler.compile(compiledOverflow, source())).hasMessageContaining("compiled schedule");
    }

    @Test void allVersionedExampleScenariosLoad() throws Exception {
        FaultScenarioLoader loader = new FaultScenarioLoader(new ObjectMapper());
        try (Stream<Path> files = Files.list(Path.of("examples", "fault-scenarios"))) {
            List<FaultScenario> scenarios = files.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> read(loader, path)).toList();
            assertThat(scenarios).hasSizeGreaterThanOrEqualTo(6).allMatch(item -> item.schemaVersion() == 1);
        }
    }

    private void assertReproducible(FaultScenario scenario) {
        assertThat(compiler.compile(scenario, source())).isEqualTo(compiler.compile(scenario, source()));
    }
    private void assertRejected(FaultSpec fault, String message) {
        assertThatThrownBy(() -> compiler.compile(scenario(fault), source())).isInstanceOf(FaultValidationException.class)
                .hasMessageContaining(message);
    }
    private FaultScenario scenario(FaultSpec... faults) { return new FaultScenario(1, "test", 42, LIMITS, List.of(faults)); }
    private FaultSelector selector(String type) { return new FaultSelector(type, null, null, null, null, null, null); }
    private FaultSpec spec(String id, FaultType type, FaultSelector selector) { return new FaultSpec(id, type, selector, Map.of()); }
    private FaultSpec spec(String id, FaultType type, FaultSelector selector, String key, Object value) {
        JsonNode node = value instanceof Integer integer ? JsonNodeFactory.instance.numberNode(integer)
                : value instanceof Long longValue ? JsonNodeFactory.instance.numberNode(longValue)
                : JsonNodeFactory.instance.textNode(value.toString());
        return new FaultSpec(id, type, selector, Map.of(key, node));
    }
    private FaultScenario read(FaultScenarioLoader loader, Path path) {
        try { return loader.readJson(Files.readString(path)); }
        catch (Exception exception) { throw new AssertionError("Could not load " + path, exception); }
    }
    private List<DomainEvent> source() {
        return List.of(event(1, "ReservationCreated"), event(2, "PaymentAuthorized"), event(3, "RefundRequested"));
    }
    private DomainEvent event(long sequence, String type) {
        Instant time = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequence);
        return new DomainEvent(new UUID(0, sequence), type, 1, AGGREGATE, new UUID(0, 20), null, "key-" + sequence,
                sequence, time, time, JsonNodeFactory.instance.objectNode().put("amount", 2500), Map.of());
    }

    private static final class ObjectNodeBuilder {
        private final Map<String, JsonNode> values = new java.util.HashMap<>();
        ObjectNodeBuilder put(String key, int value) { values.put(key, JsonNodeFactory.instance.numberNode(value)); return this; }
        ObjectNodeBuilder putObject(String key, String field, boolean value) {
            values.put(key, JsonNodeFactory.instance.objectNode().put(field, value)); return this;
        }
    }
}
