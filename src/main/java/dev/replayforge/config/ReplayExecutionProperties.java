package dev.replayforge.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("replayforge.replay-execution")
public record ReplayExecutionProperties(
        @Min(1) @Max(64) int coreThreads,
        @Min(1) @Max(64) int maxThreads,
        @Min(0) @Max(10_000) int queueCapacity,
        @Min(1) @Max(1_000_000) int maxSourceEvents,
        @Min(1) @Max(3600) int retryAfterSeconds) {
    public ReplayExecutionProperties {
        if (maxThreads < coreThreads) throw new IllegalArgumentException("maxThreads must be at least coreThreads");
    }
}
