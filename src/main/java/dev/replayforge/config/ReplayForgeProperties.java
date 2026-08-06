package dev.replayforge.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("replayforge")
public record ReplayForgeProperties(@NotBlank String instanceId) {}
