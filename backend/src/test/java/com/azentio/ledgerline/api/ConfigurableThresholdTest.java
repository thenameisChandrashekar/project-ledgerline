package com.azentio.ledgerline.api;

import com.azentio.ledgerline.engine.TestLoanFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.azentio.ledgerline.engine.TestLoanFiles.withFileId;
import static com.azentio.ledgerline.engine.TestLoanFiles.withHolder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Same file as a LOW case elsewhere becomes REFER when only configuration changes. */
@SpringBootTest(properties = {"ledgerline.rules.name-mismatch.points=35"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfigurableThresholdTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void raisingRulePointsInConfigChangesTheBand() throws Exception {
        mvc.perform(post("/api/loan-files").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(withFileId(withHolder(TestLoanFiles.clean(), "Ravi Kumar"), "LF-CFG-1"))))
                .andExpect(jsonPath("$.score").value(35))
                .andExpect(jsonPath("$.band").value("REFER"));
    }
}
