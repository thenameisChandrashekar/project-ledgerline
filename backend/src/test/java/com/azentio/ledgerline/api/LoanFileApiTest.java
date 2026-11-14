package com.azentio.ledgerline.api;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.TestLoanFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.azentio.ledgerline.engine.TestLoanFiles.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanFileApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    ResultActions postFile(Object body) throws Exception {
        return mvc.perform(post("/api/loan-files").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body)));
    }

    static LoanFile unique(LoanFile f) {
        return withFileId(f, "LF-" + UUID.randomUUID());
    }

    @Test
    void cleanFileIsLowWithNoCase() throws Exception {
        postFile(unique(TestLoanFiles.clean()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.band").value("LOW"))
                .andExpect(jsonPath("$.caseId").doesNotExist())
                .andExpect(jsonPath("$.panMasked").value("******234F"))
                .andExpect(jsonPath("$.accountNumberMasked").value("XXXXXXXX4821"));
    }

    @Test
    void suspiciousFileReturnsReasonsAndEvidenceAndOpensCase() throws Exception {
        LoanFile file = unique(withHolder(withStatementMeta(TestLoanFiles.clean(),
                new com.azentio.ledgerline.domain.DocMeta("Sejda PDF", CREATED, CREATED, 0)), "Ravi Kumar"));
        postFile(file)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(50))
                .andExpect(jsonPath("$.band").value("REFER"))
                .andExpect(jsonPath("$.ruleHits[*].ruleId", hasItem("DOC_METADATA_SUSPECT")))
                .andExpect(jsonPath("$.ruleHits[*].ruleId", hasItem("NAME_MISMATCH")))
                .andExpect(jsonPath("$.ruleHits[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.caseId").isNotEmpty());
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        ObjectNode body = json.valueToTree(unique(TestLoanFiles.clean()));
        ((ObjectNode) body.get("applicant")).put("pan", "abcde1234f");
        ((ObjectNode) body.get("salarySlips").get(0)).put("month", "2026-13");
        ((ObjectNode) body.get("bankStatement").get("transactions").get(0)).put("type", "XX");
        body.remove("employerName");
        postFile(body.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("applicant.pan")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("salarySlips[0].month")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("bankStatement.transactions[0].type")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("employerName")));
    }

    @Test
    void emptySlipAndTransactionListsAreRejected() throws Exception {
        ObjectNode body = json.valueToTree(unique(TestLoanFiles.clean()));
        body.putArray("salarySlips");
        ((ObjectNode) body.get("bankStatement")).putArray("transactions");
        postFile(body.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("salarySlips")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("bankStatement.transactions")));
    }

    @Test
    void caseEndpointsNeverExposeFullPanOrAccountNumber() throws Exception {
        LoanFile file = unique(withHolder(withDeclared(TestLoanFiles.clean(), "90000"), "Ravi Kumar"));
        String caseId = json.readTree(postFile(file).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("caseId").asText();

        String list = mvc.perform(get("/api/cases").param("status", "OPEN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String detail = mvc.perform(get("/api/cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleHits[*].evidence").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[]{list, detail}) {
            assertThat(body).doesNotContain("ABCDE1234F").doesNotContain("001234564821").contains("******234F");
        }
        assertThat(detail).contains("XXXXXXXX4821");
    }

    @Test
    void casesAreListedHighestScoreFirst() throws Exception {
        postFile(unique(withHolder(TestLoanFiles.clean(), "Ravi Kumar"))); // 20 -> LOW, no case
        postFile(unique(withDeclared(withHolder(TestLoanFiles.clean(), "Ravi Kumar"), "90000"))); // 45 REFER
        postFile(unique(withStatementMeta(withDeclared(withHolder(TestLoanFiles.clean(), "Ravi Kumar"), "90000"),
                new com.azentio.ledgerline.domain.DocMeta("iLovePDF", CREATED, CREATED, 0)))); // 75 HIGH
        var scores = json.readTree(mvc.perform(get("/api/cases").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString()).findValuesAsText("score")
                .stream().map(Integer::parseInt).toList();
        assertThat(scores).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void unknownCaseIs404() throws Exception {
        mvc.perform(get("/api/cases/does-not-exist")).andExpect(status().isNotFound());
    }
}
