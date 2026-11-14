package com.azentio.ledgerline.tools;

import com.azentio.ledgerline.engine.RulesConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetEvaluationTest {

    @Test
    void everySeededFraudFiresItsRuleAndCleanFilesStayQuiet() {
        Instant now = Instant.parse("2026-10-12T10:00:00Z");
        List<DataGenerator.GeneratedFile> files = new DataGenerator(42L, "LF", now).generate(330, 30);
        assertThat(files.stream().filter(f -> f.pattern() == SeededPattern.CLEAN).count()).isGreaterThanOrEqualTo(165);
        assertThat(files.stream().filter(f -> f.pattern() == SeededPattern.RECYCLED_DOCUMENT).count()).isEqualTo(30);

        Map<SeededPattern, DatasetEvaluator.Row> table = new DatasetEvaluator(new RulesConfig(), now).evaluate(files);
        System.out.print(DatasetEvaluator.format(table));

        table.forEach((pattern, row) -> {
            if (pattern.expectedRule() != null) {
                assertThat(row.expectedRuleFired()).as(pattern.name()).isEqualTo(row.files());
            }
        });
        DatasetEvaluator.Row clean = table.get(SeededPattern.CLEAN);
        assertThat((double) clean.referOrHigh() / clean.files()).isLessThanOrEqualTo(0.02);
    }
}
