package com.azentio.ledgerline.api;

import java.time.Instant;
import java.util.List;

public record CaseDetail(String caseId, String fileId, String applicantName, String panMasked,
                         String accountNumberMasked, int score, String band, String status, Instant createdAt,
                         Instant updatedAt, List<RuleHitView> ruleHits, List<String> degradedSignals) {
}
