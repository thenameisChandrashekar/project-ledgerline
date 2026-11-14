package com.azentio.ledgerline.engine.rules;

import com.azentio.ledgerline.domain.DocMeta;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.Rule;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Flags documents whose PDF metadata suggests they were edited after generation. Fires once per file. */
public class DocMetadataSuspectRule implements Rule {

    public static final String ID = "DOC_METADATA_SUSPECT";

    private final RulesConfig.DocMetadata config;

    public DocMetadataSuspectRule(RulesConfig config) {
        this.config = config.getDocMetadata();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(LoanFile file) {
        List<Map<String, Object>> suspects = new ArrayList<>();
        file.salarySlips().forEach(slip -> check(slip.docId(), slip.meta()).ifPresent(suspects::add));
        check(file.bankStatement().docId(), file.bankStatement().meta()).ifPresent(suspects::add);
        if (suspects.isEmpty()) {
            return Optional.empty();
        }
        String reason = "%d document(s) show signs of editing: %s".formatted(suspects.size(),
                suspects.stream().map(s -> s.get("docId") + " " + s.get("failedChecks")).toList());
        return Optional.of(new RuleHit(ID, config.getPoints(), reason, Map.of("documents", suspects)));
    }

    private Optional<Map<String, Object>> check(String docId, DocMeta meta) {
        List<String> failed = new ArrayList<>();
        String producer = meta.producer() == null ? "" : meta.producer().toLowerCase(Locale.ROOT);
        config.getEditingTools().stream()
                .filter(tool -> producer.contains(tool.toLowerCase(Locale.ROOT)))
                .findFirst()
                .ifPresent(tool -> failed.add("EDITING_TOOL_PRODUCER"));
        if (meta.incrementalUpdates() > 0) {
            failed.add("INCREMENTAL_UPDATES");
        }
        if (Duration.between(meta.createdAt(), meta.modifiedAt())
                .compareTo(Duration.ofHours(config.getMaxModifiedAfterCreatedHours())) > 0) {
            failed.add("MODIFIED_LONG_AFTER_CREATED");
        }
        if (failed.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("docId", docId);
        entry.put("failedChecks", failed);
        return Optional.of(entry);
    }
}
