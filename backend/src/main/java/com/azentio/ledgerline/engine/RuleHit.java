package com.azentio.ledgerline.engine;

import java.util.Map;

/** One rule that fired, with a human-readable reason and structured evidence. */
public record RuleHit(String ruleId, int points, String reason, Map<String, Object> evidence) {
}
