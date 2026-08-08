package dev.replayforge.api;

import dev.replayforge.replay.ReplayRun;
import dev.replayforge.replay.ReplayService;
import dev.replayforge.replay.ReplayState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import dev.replayforge.invariants.InvariantViolation;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Replay runs", description = "Run status, replay evidence, invariant violations, and divergence reports")
public final class ReplayController {
    private final ReplayService service;
    public ReplayController(ReplayService service) { this.service = service; }

    public record StartReplayRequest(@Min(0) long checkpoint, long seed, @NotNull ReplayRun.ClockMode clockMode) {}

    @PostMapping("/traces/{sourceCorrelationId}/replays")
    @Operation(summary = "Start a deterministic replay", description = "Queues an isolated replay and returns its long-running resource with a Location header.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "Fixed epoch replay", value = "{\"checkpoint\":0,\"seed\":101,\"clockMode\":\"FIXED_EPOCH\"}")))
    public ResponseEntity<ReplayRun> start(@PathVariable UUID sourceCorrelationId,
            @Valid @RequestBody StartReplayRequest request) {
        ReplayRun run = service.start(sourceCorrelationId, request.checkpoint(), request.seed(), request.clockMode());
        return ResponseEntity.accepted().location(URI.create("/api/v1/replays/" + run.replayId())).body(run);
    }

    @GetMapping("/replays/{replayId}")
    @Operation(summary = "Read replay status and execution metrics")
    public ReplayRun status(@PathVariable UUID replayId) { return service.get(replayId); }

    @GetMapping("/replays")
    @Operation(summary = "List recent replay runs")
    public List<ReplayRun> recent(@RequestParam(defaultValue = "50") @Min(1) int limit) {
        return service.recent(Math.min(limit, 200));
    }

    @GetMapping("/replays/{replayId}/trace")
    @Operation(summary = "Read the replay event timeline and fault decisions")
    public ReplayService.ReplayTrace trace(@PathVariable UUID replayId) { return service.trace(replayId); }

    @GetMapping("/replays/{replayId}/state")
    @Operation(summary = "Read completed replay final state")
    public ReplayState state(@PathVariable UUID replayId) { return service.state(replayId); }

    @GetMapping("/replays/{replayId}/report")
    @Operation(summary = "Read the divergence report envelope")
    public ReplayService.ReplayReport report(@PathVariable UUID replayId) { return service.report(replayId); }

    @GetMapping("/replays/{replayId}/violations")
    @Operation(summary = "List invariant violation evidence")
    public List<InvariantViolation> violations(@PathVariable UUID replayId) { return service.violations(replayId); }

    @GetMapping(value = "/replays/{replayId}/report.md", produces = "text/markdown")
    @Operation(summary = "Export a divergence report as Markdown")
    public String reportMarkdown(@PathVariable UUID replayId) { return service.report(replayId).markdown(); }

    @GetMapping(value = "/replays/{replayId}/report.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export a divergence report as JSON")
    public ResponseEntity<String> reportJson(@PathVariable UUID replayId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.report(replayId).json());
    }
}
