package com.azentio.ledgerline.service;

import com.azentio.ledgerline.api.LoanFileResult;

/** Result of a submission; {@code created} is false when a stored result was replayed for a repeated fileId. */
public record Submission(LoanFileResult result, boolean created) {
}
