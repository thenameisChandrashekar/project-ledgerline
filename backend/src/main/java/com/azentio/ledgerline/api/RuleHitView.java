package com.azentio.ledgerline.api;

import java.util.Map;

public record RuleHitView(String ruleId, int points, String reason, Map<String, Object> evidence) {
}
