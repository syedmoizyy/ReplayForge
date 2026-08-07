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

@RestController
@RequestMapping("/api/v1/scenarios")
public final class ScenarioController {
    @GetMapping
    public List<ScenarioSummary> list() {
        return List.of(
                new ScenarioSummary("duplicate-payment-authorized", "Duplicate payment authorization", "DUPLICATE"),
                new ScenarioSummary("dropped-refund-requested", "Drop refund request", "DROP"),
                new ScenarioSummary("dependency-timeout-retry-storm", "Timeout with bounded retries", "DEPENDENCY_TIMEOUT"));
    }

    @PostMapping("/validate")
    public ValidationResult validate(@Valid @RequestBody FaultScenario scenario) {
        return new ValidationResult(true, scenario.schemaVersion(), scenario.name(), scenario.faults().size(), scenario.limits());
    }

    public record ScenarioSummary(String id, String name, String primaryFaultType) {}
    public record ValidationResult(boolean valid, int schemaVersion, String name, int faultCount, FaultLimits limits) {}
}
