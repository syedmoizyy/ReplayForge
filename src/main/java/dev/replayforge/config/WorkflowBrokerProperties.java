package dev.replayforge.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("replayforge.workflow-broker")
public record WorkflowBrokerProperties(@NotBlank String stream, @NotBlank String deadLetterStream,
        @Min(1) int maxAttempts, @Min(1) long initialBackoffMs, @Min(1) long pollDelayMs, boolean consumersEnabled) {}
