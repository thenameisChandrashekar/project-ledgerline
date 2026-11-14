package com.azentio.ledgerline.engine.rules;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.NameNormalizer;
import com.azentio.ledgerline.engine.Rule;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Statement holder and slip employee names must be the applicant, ignoring case, spaces, dots and order. */
public class NameMismatchRule implements Rule {

    public static final String ID = "NAME_MISMATCH";

    private final RulesConfig.NameMismatch config;

    public NameMismatchRule(RulesConfig config) {
        this.config = config.getNameMismatch();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(LoanFile file) {
        String applicant = NameNormalizer.person(file.applicant().name());
        List<Map<String, Object>> mismatches = new ArrayList<>();
        if (!applicant.equals(NameNormalizer.person(file.bankStatement().holderName()))) {
            mismatches.add(mismatch(file.bankStatement().docId(), "holderName", file.bankStatement().holderName()));
        }
        file.salarySlips().stream()
                .filter(slip -> !applicant.equals(NameNormalizer.person(slip.employeeName())))
                .forEach(slip -> mismatches.add(mismatch(slip.docId(), "employeeName", slip.employeeName())));
        if (mismatches.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("applicantName", file.applicant().name());
        evidence.put("mismatches", mismatches);
        String reason = "%d document name(s) do not match applicant '%s'".formatted(mismatches.size(),
                file.applicant().name());
        return Optional.of(new RuleHit(ID, config.getPoints(), reason, evidence));
    }

    private static Map<String, Object> mismatch(String docId, String field, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docId", docId);
        m.put("field", field);
        m.put("value", value);
        return m;
    }
}
