package dev.replayforge.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.replay.DeterministicReplayEngine;
import dev.replayforge.replay.ReplayRun;
import dev.replayforge.support.EventFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "replayforge.benchmark", matches = "true")
class ReplayBenchmark {
    private static final int[] SIZES = {10, 100, 1_000, 10_000};
    private static final int WARMUPS = 3;
    private static final int ITERATIONS = 15;

    @Test void measureReplayLatencyAndThroughput() throws Exception {
        DeterministicReplayEngine engine = new DeterministicReplayEngine();
        List<Map<String, Object>> results = new ArrayList<>();
        for (int size : SIZES) {
            List<DomainEvent> trace = trace(size);
            for (int i = 0; i < WARMUPS; i++) engine.execute(trace, 0, 42, ReplayRun.ClockMode.FIXED_EPOCH);
            List<Long> nanos = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                long started = System.nanoTime();
                engine.execute(trace, 0, 42, ReplayRun.ClockMode.FIXED_EPOCH);
                nanos.add(System.nanoTime() - started);
            }
            nanos.sort(Long::compare);
            double medianMs = nanos.get(ITERATIONS / 2) / 1_000_000d;
            double p95Ms = nanos.get((int) Math.ceil(ITERATIONS * .95) - 1) / 1_000_000d;
            results.add(Map.of("traceSize", size, "iterations", ITERATIONS, "medianLatencyMs", medianMs,
                    "p95LatencyMs", p95Ms, "medianThroughputEventsPerSecond", size / (medianMs / 1000d)));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("environment", Map.of("os", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "architecture", System.getProperty("os.arch"), "java", System.getProperty("java.version"),
                "processors", Runtime.getRuntime().availableProcessors(), "maxHeapBytes", Runtime.getRuntime().maxMemory()));
        report.put("method", Map.of("warmups", WARMUPS, "iterations", ITERATIONS, "clockMode", "FIXED_EPOCH", "seed", 42));
        report.put("results", results);
        Path output = Path.of("target", "benchmark", "replay-benchmark.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private List<DomainEvent> trace(int size) {
        UUID aggregate = UUID.randomUUID(); UUID correlation = UUID.randomUUID();
        return LongStream.rangeClosed(1, size).mapToObj(sequence -> EventFixture.event().aggregateId(aggregate)
                .correlationId(correlation).sequence(sequence).build()).toList();
    }
}
