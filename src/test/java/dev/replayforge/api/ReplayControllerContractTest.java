package dev.replayforge.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.replayforge.replay.ReplayRun;
import dev.replayforge.replay.ReplayService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReplayController.class)
class ReplayControllerContractTest {
    @Autowired MockMvc mvc;
    @MockBean ReplayService service;

    @Test void startsReplayWithStableAcceptedContract() throws Exception {
        UUID correlationId = UUID.randomUUID();
        ReplayRun run = run(correlationId, ReplayRun.Status.QUEUED);
        when(service.start(correlationId, 0, 101, ReplayRun.ClockMode.FIXED_EPOCH)).thenReturn(run);

        mvc.perform(post("/api/v1/traces/{id}/replays", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkpoint\":0,\"seed\":101,\"clockMode\":\"FIXED_EPOCH\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/replays/" + run.replayId()))
                .andExpect(jsonPath("$.sourceCorrelationId").value(correlationId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test void listsAndReadsReplayContracts() throws Exception {
        UUID correlationId = UUID.randomUUID();
        ReplayRun run = run(correlationId, ReplayRun.Status.RUNNING);
        when(service.recent(25)).thenReturn(List.of(run));
        when(service.get(run.replayId())).thenReturn(run);

        mvc.perform(get("/api/v1/replays").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].replayId").value(run.replayId().toString()))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
        mvc.perform(get("/api/v1/replays/{id}", run.replayId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seed").value(101));
    }

    private static ReplayRun run(UUID correlationId, ReplayRun.Status status) {
        return new ReplayRun(UUID.randomUUID(), correlationId, 0, 101, ReplayRun.ClockMode.FIXED_EPOCH,
                status, Instant.parse("2026-01-01T00:00:00Z"), null, null, null, null, null);
    }
}
