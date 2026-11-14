package com.azentio.ledgerline.api;

import java.time.Instant;

public record AuditEntryView(String caseId, String fromStatus, String toStatus, String actor, String comment, Instant at) {
}
