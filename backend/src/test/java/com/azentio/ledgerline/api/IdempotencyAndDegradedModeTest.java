package com.azentio.ledgerline.api;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.TestLoanFiles;
import com.azentio.ledgerline.fingerprint.DocumentFingerprintStore;
import com.azentio.ledgerline.fingerprint.FingerprintEntry;
import com.azentio.ledgerline.persistence.CaseRepository;
import com.azentio.ledgerline.persistence.LoanFileRepository;
import com.azentio.ledgerline.persistence.RuleHitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.azentio.ledgerline.engine.TestLoanFiles.withDeclared;
import static com.azentio.ledgerline.engine.TestLoanFiles.withFileId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyAndDegradedModeTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    LoanFileRepository loanFiles;
    @Autowired
    RuleHitRepository ruleHits;
    @Autowired
    CaseRepository cases;
    @SpyBean
    DocumentFingerprintStore store;

    @BeforeEach
    void resetStore() {
        reset(store);
    }

    ResultActions postFile(LoanFile file) throws Exception {
        return mvc.perform(post("/api/loan-files").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(file)));
    }

    @Test
    void sameFileIdAndPayloadTwiceStoresOnceAndReplays() throws Exception {
        // Name mismatch + inflated declaration = 45 -> REFER, so a case is opened.
        LoanFile file = withFileId(TestLoanFiles.withHolder(withDeclared(TestLoanFiles.clean(), "90000"), "Ravi Kumar"),
                "LF-IDEMP-1");
        String first = postFile(file).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = postFile(file).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(second)).isEqualTo(json.readTree(first));
        Long id = loanFiles.findByFileId("LF-IDEMP-1").orElseThrow().getId();
        assertThat(loanFiles.findAll().stream().filter(f -> f.getFileId().equals("LF-IDEMP-1"))).hasSize(1);
        assertThat(ruleHits.findByLoanFileIdOrderById(id)).hasSize(2);
        assertThat(cases.findAll().stream().filter(c -> c.getLoanFileId().equals(id))).hasSize(1);
    }

    @Test
    void replayDoesNotAddFingerprintsAgain() throws Exception {
        LoanFile file = withFileId(TestLoanFiles.clean(), "LF-IDEMP-FP");
        postFile(file).andExpect(status().isCreated());
        postFile(file).andExpect(status().isOk());
        org.mockito.Mockito.verify(store, org.mockito.Mockito.times(2))
                .add(anyString(), any(FingerprintEntry.class), any(Instant.class), any(Duration.class));
    }

    @Test
    void sameFileIdWithChangedPayloadIsConflict() throws Exception {
        LoanFile file = withFileId(TestLoanFiles.clean(), "LF-IDEMP-2");
        postFile(file).andExpect(status().isCreated());
        postFile(withDeclared(file, "53000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void storeFailureDegradesSignalAndAppliesReferFloor() throws Exception {
        doThrow(new IllegalStateException("redis down")).when(store).findSince(anyString(), any(Instant.class));
        postFile(withFileId(TestLoanFiles.clean(), "LF-DEGRADED-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.band").value("REFER"))
                .andExpect(jsonPath("$.degradedSignals[0]").value("DOCUMENT_REUSED"))
                .andExpect(jsonPath("$.caseId").isNotEmpty());
    }

    @Test
    void slowStoreTimesOutAndDegrades() throws Exception {
        doAnswer(inv -> {
            Thread.sleep(500);
            return List.of();
        }).when(store).findSince(anyString(), any(Instant.class));
        long start = System.nanoTime();
        postFile(withFileId(TestLoanFiles.clean(), "LF-DEGRADED-2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.band").value("REFER"))
                .andExpect(jsonPath("$.degradedSignals[0]").value("DOCUMENT_REUSED"));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(450));
    }

    @Test
    void recycledStatementAcrossTheApiFiresDocumentReused() throws Exception {
        LoanFile source = withFileId(TestLoanFiles.clean(), "LF-REUSE-SRC");
        postFile(source).andExpect(status().isCreated());
        var s = source.bankStatement();
        LoanFile copy = new LoanFile("LF-REUSE-COPY", source.submittedAt(),
                new com.azentio.ledgerline.domain.Applicant("Ravi Kumar", "ZZZPK9999Z"), source.declaredMonthlyIncome(),
                source.employerName(), source.salarySlips().stream().map(sl -> new com.azentio.ledgerline.domain.SalarySlip(
                sl.docId(), sl.month(), "Ravi Kumar", sl.employerName(), sl.grossPay(), sl.netPay(), sl.meta())).toList(),
                new com.azentio.ledgerline.domain.BankStatement("STMT-COPY", "999988887777", "Ravi Kumar",
                        s.openingBalance(), s.transactions(), s.meta()));
        postFile(copy)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleHits[0].ruleId").value("DOCUMENT_REUSED"))
                .andExpect(jsonPath("$.ruleHits[0].evidence.otherFileIds", org.hamcrest.Matchers.hasItem("LF-REUSE-SRC")))
                .andExpect(jsonPath("$.band").value("HIGH"));
    }
}
