package dev.replayforge.api;

import dev.replayforge.faults.FaultLimits;
import dev.replayforge.faults.FaultScenario;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(name = "Fault scenarios", description = "Versioned, bounded fault plans for isolated deterministic replay")
public final class ScenarioController {
    @GetMapping
    @Operation(summary = "List seeded fault scenarios")
    public List<ScenarioSummary> list() {
        return List.of(
                new ScenarioSummary("duplicate-payment-authorized", "Duplicate payment authorization", "DUPLICATE"),
                new ScenarioSummary("dropped-refund-requested", "Drop refund request", "DROP"),
                new ScenarioSummary("dependency-timeout-retry-storm", "Timeout with bounded retries", "DEPENDENCY_TIMEOUT"));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a fault scenario without running it")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Duplicate authorization", value = """
            {"schemaVersion":1,"name":"duplicate authorization","seed":101,
             "limits":{"maxDuplicates":3,"maxDelayMillis":30000,"maxRetries":5,"maxEvents":100},
             "faults":[{"id":"duplicate-payment","type":"DUPLICATE",
             "selector":{"eventType":"DepositAuthorized"},"parameters":{"count":1}}]}""")))
    public ValidationResult validate(@Valid @RequestBody FaultScenario scenario) {
        return new ValidationResult(true, scenario.schemaVersion(), scenario.name(), scenario.faults().size(), scenario.limits());
    }

    public record ScenarioSummary(String id, String name, String primaryFaultType) {}
    public record ValidationResult(boolean valid, int schemaVersion, String name, int faultCount, FaultLimits limits) {}
}
