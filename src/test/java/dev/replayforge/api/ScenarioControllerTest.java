package dev.replayforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {
    @Autowired MockMvc mvc;

    @Test void listsVersionedScenarioCatalog() throws Exception {
        mvc.perform(get("/api/v1/scenarios")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("duplicate-payment-authorized"))
                .andExpect(jsonPath("$[0].primaryFaultType").value("DUPLICATE"));
    }

    @Test void validatesScenarioContract() throws Exception {
        mvc.perform(post("/api/v1/scenarios/validate").contentType(MediaType.APPLICATION_JSON).content("""
                {"schemaVersion":1,"name":"duplicate authorization","seed":101,
                 "limits":{"maxDuplicates":3,"maxDelayMillis":30000,"maxRetries":5,"maxEvents":100},
                 "faults":[{"id":"duplicate-payment","type":"DUPLICATE","selector":{"eventType":"DepositAuthorized"},"parameters":{"count":1}}]}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.faultCount").value(1));
    }
}
