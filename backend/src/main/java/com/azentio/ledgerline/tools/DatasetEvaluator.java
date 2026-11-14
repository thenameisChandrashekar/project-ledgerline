package com.azentio.ledgerline.tools;

import com.azentio.ledgerline.engine.Band;
import com.azentio.ledgerline.engine.RiskBander;
import com.azentio.ledgerline.engine.RuleEngine;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;
import com.azentio.ledgerline.fingerprint.DocumentReuseChecker;
import com.azentio.ledgerline.fingerprint.InMemoryFingerprintStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs generated files through the rules offline and reports, per seeded pattern, how many fired the expected rule
 * and how many CLEAN files were flagged. Usage: {@code DatasetEvaluator [count] [seed] [recycled]}.
 */
public class DatasetEvaluator {

    public static final class Row {
        int files;
        int expectedRuleFired;
        int anyRuleFired;
        int referOrHigh;

        public int files() { return files; }
        public int expectedRuleFired() { return expectedRuleFired; }
        public int anyRuleFired() { return anyRuleFired; }
        public int referOrHigh() { return referOrHigh; }
    }

    private final RuleEngine engine;
    private final RiskBander bander;
    private final DocumentReuseChecker reuseChecker;

    /** Files are evaluated in order with an in-memory fingerprint store, as the API would see them. */
    public DatasetEvaluator(RulesConfig config, Instant now) {
        this.engine = RuleEngine.withDefaultRules(config);
        this.bander = new RiskBander(config);
        this.reuseChecker = new DocumentReuseChecker(new InMemoryFingerprintStore(), Clock.fixed(now, ZoneOffset.UTC), config);
    }

    public Map<SeededPattern, Row> evaluate(List<DataGenerator.GeneratedFile> files) {
        Map<SeededPattern, Row> table = new EnumMap<>(SeededPattern.class);
        for (DataGenerator.GeneratedFile generated : files) {
            List<RuleHit> hits = new ArrayList<>(engine.evaluate(generated.file()));
            reuseChecker.check(generated.file()).ifPresent(hits::add);
            reuseChecker.record(generated.file());
            Band band = bander.band(hits.stream().mapToInt(RuleHit::points).sum());
            Row row = table.computeIfAbsent(generated.pattern(), p -> new Row());
            row.files++;
            String expected = generated.pattern().expectedRule();
            if (expected != null && hits.stream().anyMatch(h -> h.ruleId().equals(expected))) {
                row.expectedRuleFired++;
            }
            if (!hits.isEmpty()) {
                row.anyRuleFired++;
            }
            if (band.opensCase()) {
                row.referOrHigh++;
            }
        }
        return table;
    }

    public static String format(Map<SeededPattern, Row> table) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Seeded pattern | Files | Expected rule | Fired expected rule | Fired any rule | REFER/HIGH |\n");
        sb.append("|---|---|---|---|---|---|\n");
        table.forEach((pattern, row) -> sb.append("| %s | %d | %s | %s | %d | %d |%n".formatted(pattern, row.files,
                pattern.expectedRule() == null ? "-" : pattern.expectedRule(),
                pattern.expectedRule() == null ? "-" : row.expectedRuleFired + "/" + row.files,
                row.anyRuleFired, row.referOrHigh)));
        return sb.toString();
    }

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 330;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        int recycled = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        Instant now = Instant.parse("2026-10-12T10:00:00Z");
        List<DataGenerator.GeneratedFile> files = new DataGenerator(seed, "LF", now).generate(count, recycled);
        System.out.print(format(new DatasetEvaluator(new RulesConfig(), now).evaluate(files)));
    }
}
