package com.azentio.ledgerline.api;

import com.azentio.ledgerline.engine.TestLoanFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static com.azentio.ledgerline.engine.TestLoanFiles.withDeclared;
import static com.azentio.ledgerline.engine.TestLoanFiles.withFileId;
import static com.azentio.ledgerline.engine.TestLoanFiles.withHolder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DispositionApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String caseId;

    @BeforeEach
    void openCase() throws Exception {
        String body = json.writeValueAsString(withFileId(withHolder(withDeclared(TestLoanFiles.clean(), "90000"), "Ravi Kumar"),
                "LF-DISP-" + UUID.randomUUID()));
        caseId = json.readTree(mvc.perform(post("/api/loan-files").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("caseId").asText();
    }

    ResultActions dispose(String disposition, String comment, String user) throws Exception {
        var request = post("/api/cases/" + caseId + "/disposition").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("disposition", disposition, "comment", comment)));
        return mvc.perform(user == null ? request : request.header("X-User", user));
    }

    @Test
    void validTransitionUpdatesCaseAndWritesAuditRow() throws Exception {
        dispose("REFER_FIELD_VERIFICATION", "Employer call-back needed before sanction", "analyst.priya")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFER_FIELD_VERIFICATION"));

        mvc.perform(get("/api/cases/" + caseId + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].toStatus").value("REFER_FIELD_VERIFICATION"))
                .andExpect(jsonPath("$[0].actor").value("analyst.priya"))
                .andExpect(jsonPath("$[0].comment").value("Employer call-back needed before sanction"));

        dispose("FRAUD_CONFIRMED", "Employer confirmed applicant never worked there", "analyst.priya")
                .andExpect(status().isOk());
        mvc.perform(get("/api/cases/" + caseId + "/audit")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void actingOnAFinalCaseIsConflictAndWritesNoAudit() throws Exception {
        dispose("CLEARED", "Documents verified with the bank branch", "analyst.ravi").andExpect(status().isOk());
        dispose("FRAUD_CONFIRMED", "Changing my mind after clearing it", "analyst.ravi")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot move case from CLEARED to FRAUD_CONFIRMED"));
        mvc.perform(get("/api/cases/" + caseId + "/audit")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shortOrMissingCommentIsBadRequest() throws Exception {
        dispose("CLEARED", "too short", "analyst.ravi")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
        mvc.perform(post("/api/cases/" + caseId + "/disposition").header("X-User", "analyst.ravi")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disposition\":\"CLEARED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cases/" + caseId + "/audit")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void missingUserHeaderOrUnknownDispositionIsBadRequest() throws Exception {
        dispose("CLEARED", "Documents verified with the bank branch", null).andExpect(status().isBadRequest());
        dispose("REOPEN", "Documents verified with the bank branch", "analyst.ravi").andExpect(status().isBadRequest());
    }
}
