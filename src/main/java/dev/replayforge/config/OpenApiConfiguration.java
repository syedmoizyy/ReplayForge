package dev.replayforge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean OpenAPI replayForgeOpenApi() {
        return new OpenAPI().info(new Info().title("ReplayForge API").version("v1")
                .description("Deterministic workflow trace capture, scenario validation, replay, invariant evidence, and divergence reports.")
                .license(new License().name("Internal development")));
    }
}
