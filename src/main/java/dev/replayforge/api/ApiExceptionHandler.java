package dev.replayforge.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import dev.replayforge.sampleworkload.WorkflowTransitionException;
import dev.replayforge.replay.ReplayNotFoundException;
import dev.replayforge.replay.ReplayValidationException;
import dev.replayforge.replay.ReplayCapacityException;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> validation(MethodArgumentNotValidException error) {
        return Map.of("code", "VALIDATION_FAILED", "message", error.getMessage(), "timestamp", Instant.now().toString());
    }

    @ExceptionHandler(WorkflowTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> transition(WorkflowTransitionException error) {
        return Map.of("code", "INVALID_WORKFLOW_TRANSITION", "message", error.getMessage(), "timestamp", Instant.now().toString());
    }

    @ExceptionHandler(ReplayValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, Object> replayValidation(ReplayValidationException error) {
        return Map.of("code", "INVALID_REPLAY", "message", error.getMessage(), "timestamp", Instant.now().toString());
    }

    @ExceptionHandler(ReplayNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> replayNotFound(ReplayNotFoundException error) {
        return Map.of("code", "REPLAY_NOT_FOUND", "message", error.getMessage(), "timestamp", Instant.now().toString());
    }

    @ExceptionHandler(ReplayCapacityException.class)
    ResponseEntity<Map<String, Object>> replayCapacity(ReplayCapacityException error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Integer.toString(error.retryAfterSeconds()))
                .body(Map.of("code", "REPLAY_CAPACITY_EXHAUSTED", "message", error.getMessage(),
                        "timestamp", Instant.now().toString()));
    }
}
