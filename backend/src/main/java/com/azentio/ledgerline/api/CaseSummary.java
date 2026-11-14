package com.azentio.ledgerline.api;

import java.time.Instant;

public record CaseSummary(String caseId, String fileId, String applicantName, String panMasked, int score, String band,
                          String status, Instant createdAt) {
}
