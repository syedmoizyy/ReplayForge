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

@RestController
@RequestMapping("/api/v1")
@Validated
public final class ReplayController {
    private final ReplayService service;
    public ReplayController(ReplayService service) { this.service = service; }

    public record StartReplayRequest(@Min(0) long checkpoint, long seed, @NotNull ReplayRun.ClockMode clockMode) {}

    @PostMapping("/traces/{sourceCorrelationId}/replays")
    public ResponseEntity<ReplayRun> start(@PathVariable UUID sourceCorrelationId,
            @Valid @RequestBody StartReplayRequest request) {
        ReplayRun run = service.start(sourceCorrelationId, request.checkpoint(), request.seed(), request.clockMode());
        return ResponseEntity.accepted().location(URI.create("/api/v1/replays/" + run.replayId())).body(run);
    }

    @GetMapping("/replays/{replayId}")
    public ReplayRun status(@PathVariable UUID replayId) { return service.get(replayId); }

    @GetMapping("/replays/{replayId}/trace")
    public ReplayService.ReplayTrace trace(@PathVariable UUID replayId) { return service.trace(replayId); }

    @GetMapping("/replays/{replayId}/state")
    public ReplayState state(@PathVariable UUID replayId) { return service.state(replayId); }

    @GetMapping("/replays/{replayId}/report")
    public ReplayService.ReplayReport report(@PathVariable UUID replayId) { return service.report(replayId); }
}
