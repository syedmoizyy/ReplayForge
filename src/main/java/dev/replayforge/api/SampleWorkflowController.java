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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({"/api/v1/workflows", "/api/v1/sample-workflows"})
@Validated
@Tag(name = "Workflows", description = "Seeded neutral reservation workflows used to capture demonstration traces")
public final class SampleWorkflowController {
    private final WorkflowEngine engine;
    public SampleWorkflowController(WorkflowEngine engine) { this.engine = engine; }

    public record StartRequest(@Min(1) long depositAmount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency, boolean autoPayout) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start a seeded sample workflow")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            value = "{\"depositAmount\":12500,\"currency\":\"USD\",\"autoPayout\":true}")))
    public DomainEvent start(@Valid @RequestBody StartRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        return engine.start(request.depositAmount(), request.currency(), request.autoPayout(), idempotencyKey);
    }

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Cancel a sample workflow and request its refund")
    public DomainEvent cancel(@PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        return engine.cancel(reservationId, idempotencyKey);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "Read sample workflow projection state")
    public ReservationProjection state(@PathVariable UUID reservationId) { return engine.state(reservationId); }
}
