package com.azentio.ledgerline.api;

import java.util.List;

/** Response of POST /api/loan-files. Identifiers are always masked. */
public record LoanFileResult(
        String fileId,
        String applicantName,
        String panMasked,
        String accountNumberMasked,
        int score,
        String band,
        List<RuleHitView> ruleHits,
        List<String> degradedSignals,
        String caseId) {
}
