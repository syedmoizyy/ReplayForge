package dev.replayforge.config;

import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;
import dev.replayforge.observability.ReplayTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.replayforge.divergence.DivergenceReporter;
import dev.replayforge.invariants.InvariantEngine;
import dev.replayforge.replay.DeterministicReplayEngine;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReplayExecutionConfiguration {
    @Bean public Clock systemClock() { return Clock.systemUTC(); }
    @Bean public ReplayTelemetry replayTelemetry(MeterRegistry registry) { return new ReplayTelemetry(registry); }
    @Bean public InvariantEngine invariantEngine() { return InvariantEngine.standard(); }
    @Bean public DivergenceReporter divergenceReporter(ObjectMapper mapper) { return new DivergenceReporter(mapper); }
    @Bean public DeterministicReplayEngine deterministicReplayEngine(InvariantEngine invariants,
            DivergenceReporter reporter, ReplayTelemetry telemetry) {
        return new DeterministicReplayEngine(invariants, reporter, telemetry);
    }
    @Bean(name = "replayExecutor") public Executor replayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("replay-");
        executor.initialize();
        return executor;
    }
}
