package com.azentio.ledgerline.engine;

import com.azentio.ledgerline.domain.DocMeta;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.domain.Transaction;
import com.azentio.ledgerline.engine.rules.BalanceBreakRule;
import com.azentio.ledgerline.engine.rules.DeclaredIncomeAboveSlipsRule;
import com.azentio.ledgerline.engine.rules.DocMetadataSuspectRule;
import com.azentio.ledgerline.engine.rules.NameMismatchRule;
import com.azentio.ledgerline.engine.rules.SalaryCreditMissingRule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.azentio.ledgerline.engine.TestLoanFiles.*;
import static org.assertj.core.api.Assertions.assertThat;

class RulesTest {

    private final RulesConfig config = new RulesConfig();

    @Test
    void cleanFileFiresNoRule() {
        assertThat(RuleEngine.withDefaultRules(config).evaluate(clean())).isEmpty();
    }

    @Nested
    class BalanceBreak {
        private final BalanceBreakRule rule = new BalanceBreakRule(config);

        @Test
        void reconcilingStatementDoesNotFire() {
            assertThat(rule.evaluate(clean())).isEmpty();
        }

        @Test
        void singleEditedBalanceInTheMiddleFiresWithThatRowIndex() {
            List<Transaction> txns = new ArrayList<>(cleanTransactions());
            // Row 2 (EMI debit) balance lifted by 5,000 although the amount is unchanged.
            txns.set(2, txn("2026-09-10", "ACH/HDFC HOME LOAN EMI", "DR", "18000.00", "48550.00"));
            var hit = rule.evaluate(withTransactions(txns)).orElseThrow();
            assertThat(hit.points()).isEqualTo(50);
            assertThat(hit.evidence()).containsEntry("rowIndex", 2).containsEntry("date", "2026-09-10");
            assertThat((BigDecimal) hit.evidence().get("expectedBalance")).isEqualByComparingTo("43550.00");
            assertThat((BigDecimal) hit.evidence().get("statedBalance")).isEqualByComparingTo("48550.00");
        }

        @Test
        void differenceWithinOnePaisaIsTolerated() {
            List<Transaction> txns = new ArrayList<>(cleanTransactions());
            txns.set(0, txn("2026-08-05", "UPI/SWIGGY/FOOD", "DR", "450.00", "9550.01"));
            txns.set(1, txn("2026-09-02", "NEFT/NORTHWIND ANALYTICS PRIVATE LIMITED/SAL AUG", "CR", "52000.00", "61550.01"));
            txns.set(2, txn("2026-09-10", "ACH/HDFC HOME LOAN EMI", "DR", "18000.00", "43550.00"));
            assertThat(rule.evaluate(withTransactions(txns))).isEmpty();
        }
    }

    @Nested
    class SalaryCreditMissing {
        private final SalaryCreditMissingRule rule = new SalaryCreditMissingRule(config);

        @Test
        void creditOnThirdOfNextMonthWithOnePercentDiffAndOtherNarrationDoesNotFire() {
            List<Transaction> txns = List.of(
                    txn("2026-09-03", "BY TRANSFER-NEFT*NORTHWIND ANALYTICS LTD*SALARY", "CR", "51480.00", "61480.00"),
                    txn("2026-10-03", "IMPS NORTHWIND ANALYTICS PVT LTD SAL", "CR", "52520.00", "114000.00"));
            assertThat(rule.evaluate(withTransactions(txns))).isEmpty();
        }

        @Test
        void slipWithoutAnyCreditFiresWithUnmatchedMonth() {
            List<Transaction> txns = new ArrayList<>(cleanTransactions());
            txns.remove(4); // September salary credit
            var hit = rule.evaluate(withTransactions(txns)).orElseThrow();
            assertThat(hit.evidence()).isEqualTo(Map.of("unmatchedMonths", List.of("2026-09")));
        }

        @Test
        void creditAfterGraceWindowDoesNotCountForTheSlipMonth() {
            // 9 September is past the 7-day grace for August, but inside September's own window.
            List<Transaction> txns = List.of(
                    txn("2026-09-09", "NEFT/NORTHWIND ANALYTICS/SAL AUG", "CR", "52000.00", "62000.00"));
            var hit = rule.evaluate(withTransactions(txns)).orElseThrow();
            assertThat(hit.evidence()).isEqualTo(Map.of("unmatchedMonths", List.of("2026-08")));
        }

        @Test
        void creditFromDifferentEmployerDoesNotCount() {
            List<Transaction> txns = List.of(
                    txn("2026-09-02", "NEFT/CONTOSO TRADING/SAL AUG", "CR", "52000.00", "62000.00"),
                    txn("2026-10-01", "NEFT/CONTOSO TRADING/SAL SEP", "CR", "52000.00", "114000.00"));
            var hit = rule.evaluate(withTransactions(txns)).orElseThrow();
            assertThat(hit.evidence()).isEqualTo(Map.of("unmatchedMonths", List.of("2026-08", "2026-09")));
        }

        @Test
        void creditMoreThanTwoPercentOffDoesNotCount() {
            List<Transaction> txns = new ArrayList<>(cleanTransactions());
            txns.set(4, txn("2026-10-01", "NEFT/NORTHWIND ANALYTICS PRIVATE LIMITED/SAL SEP", "CR", "53100.00", "94339.50"));
            assertThat(rule.evaluate(withTransactions(txns))).isPresent();
        }
    }

    @Nested
    class DeclaredIncome {
        private final DeclaredIncomeAboveSlipsRule rule = new DeclaredIncomeAboveSlipsRule(config);

        @Test
        void exactlyOnePointTwoTimesDoesNotFire() {
            assertThat(rule.evaluate(withDeclared(clean(), "62400"))).isEmpty();
        }

        @Test
        void aboveOnePointTwoTimesFires() {
            var hit = rule.evaluate(withDeclared(clean(), "62401")).orElseThrow();
            assertThat(hit.points()).isEqualTo(25);
            assertThat((BigDecimal) hit.evidence().get("averageSlipNetPay")).isEqualByComparingTo("52000");
        }
    }

    @Nested
    class DocMetadata {
        private final DocMetadataSuspectRule rule = new DocMetadataSuspectRule(config);

        @Test
        void cleanMetadataDoesNotFire() {
            assertThat(rule.evaluate(clean())).isEmpty();
        }

        @Test
        void editingToolProducerIsCaseInsensitive() {
            LoanFile f = withStatementMeta(clean(), new DocMeta("ilovepdf.com", CREATED, CREATED, 0));
            var hit = rule.evaluate(f).orElseThrow();
            assertThat(hit.evidence().toString()).contains("STMT-1", "EDITING_TOOL_PRODUCER");
        }

        @Test
        void incrementalUpdatesAndLateModificationAreReportedTogether() {
            LoanFile f = withStatementMeta(clean(), new DocMeta("Bank eStatement", CREATED, CREATED.plusSeconds(25 * 3600), 2));
            var hit = rule.evaluate(f).orElseThrow();
            assertThat(hit.evidence().toString()).contains("INCREMENTAL_UPDATES", "MODIFIED_LONG_AFTER_CREATED");
        }

        @Test
        void modifiedExactlyTwentyFourHoursLaterDoesNotFire() {
            LoanFile f = withStatementMeta(clean(), new DocMeta("Bank eStatement", CREATED, CREATED.plusSeconds(24 * 3600), 0));
            assertThat(rule.evaluate(f)).isEmpty();
        }
    }

    @Nested
    class NameMismatch {
        private final NameMismatchRule rule = new NameMismatchRule(config);

        @Test
        void caseSpacingDotsAndOrderAreIgnored() {
            assertThat(rule.evaluate(withHolder(clean(), "  RAO   asha. "))).isEmpty();
        }

        @Test
        void differentHolderFires() {
            var hit = rule.evaluate(withHolder(clean(), "Ravi Kumar")).orElseThrow();
            assertThat(hit.points()).isEqualTo(20);
            assertThat(hit.evidence().toString()).contains("holderName", "Ravi Kumar");
        }
    }

    @Nested
    class Banding {
        private final RiskBander bander = new RiskBander(config);

        @Test
        void boundaries() {
            assertThat(bander.band(29)).isEqualTo(Band.LOW);
            assertThat(bander.band(30)).isEqualTo(Band.REFER);
            assertThat(bander.band(59)).isEqualTo(Band.REFER);
            assertThat(bander.band(60)).isEqualTo(Band.HIGH);
        }

        @Test
        void thresholdsComeFromConfig() {
            config.getBands().setRefer(20);
            assertThat(new RiskBander(config).band(25)).isEqualTo(Band.REFER);
        }
    }

    @Test
    void employerNormalisationMatchesSpecExample() {
        assertThat(NameNormalizer.narrationContainsEmployer("NEFT/NORTHWIND ANALYTICS PRIVATE LIMITED/SAL AUG",
                "Northwind Analytics Pvt. Ltd.")).isTrue();
        assertThat(NameNormalizer.narrationContainsEmployer("NEFT/NORTHWINDS ANALYTICS/SAL", "Northwind Analytics")).isFalse();
    }
}
