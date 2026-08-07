package dev.replayforge.api;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.workflow.ReservationProjection;
import dev.replayforge.sampleworkload.WorkflowEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping({"/api/v1/workflows", "/api/v1/sample-workflows"})
@Validated
public final class SampleWorkflowController {
    private final WorkflowEngine engine;
    public SampleWorkflowController(WorkflowEngine engine) { this.engine = engine; }

    public record StartRequest(@Min(1) long depositAmount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency, boolean autoPayout) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DomainEvent start(@Valid @RequestBody StartRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        return engine.start(request.depositAmount(), request.currency(), request.autoPayout(), idempotencyKey);
    }

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DomainEvent cancel(@PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        return engine.cancel(reservationId, idempotencyKey);
    }

    @GetMapping("/{reservationId}")
    public ReservationProjection state(@PathVariable UUID reservationId) { return engine.state(reservationId); }
}
