package com.azentio.ledgerline.service;

import com.azentio.ledgerline.api.CaseDetail;
import com.azentio.ledgerline.api.CaseSummary;
import com.azentio.ledgerline.api.LoanFileResult;
import com.azentio.ledgerline.api.RuleHitView;
import com.azentio.ledgerline.masking.Masking;
import com.azentio.ledgerline.persistence.CaseEntity;
import com.azentio.ledgerline.persistence.LoanFileEntity;
import com.azentio.ledgerline.persistence.RuleHitEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Builds API views from stored rows. The only place identifiers leave the service, so masking happens here. */
@Component
public class ResultMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LoanFileResult toResult(LoanFileEntity file, List<RuleHitEntity> hits, CaseEntity caseEntity) {
        return new LoanFileResult(file.getFileId(), file.getApplicantName(), Masking.pan(file.getPan()),
                Masking.account(file.getAccountNumber()), file.getScore(), file.getBand(), views(hits),
                degraded(file), caseEntity == null ? null : caseEntity.getCaseId());
    }

    public CaseSummary toSummary(CaseEntity c, LoanFileEntity file) {
        return new CaseSummary(c.getCaseId(), c.getFileId(), file.getApplicantName(), Masking.pan(file.getPan()),
                c.getScore(), c.getBand(), c.getStatus().name(), c.getCreatedAt());
    }

    public CaseDetail toDetail(CaseEntity c, LoanFileEntity file, List<RuleHitEntity> hits) {
        return new CaseDetail(c.getCaseId(), c.getFileId(), file.getApplicantName(), Masking.pan(file.getPan()),
                Masking.account(file.getAccountNumber()), c.getScore(), c.getBand(), c.getStatus().name(),
                c.getCreatedAt(), c.getUpdatedAt(), views(hits), degraded(file));
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise value", e);
        }
    }

    private List<RuleHitView> views(List<RuleHitEntity> hits) {
        return hits.stream()
                .map(h -> new RuleHitView(h.getRuleId(), h.getPoints(), h.getReason(), readEvidence(h.getEvidence())))
                .toList();
    }

    private Map<String, Object> readEvidence(String json) {
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored evidence is not valid JSON", e);
        }
    }

    private static List<String> degraded(LoanFileEntity file) {
        String raw = file.getDegradedSignals();
        return raw == null || raw.isBlank() ? List.of() : Arrays.asList(raw.split(","));
    }
}
