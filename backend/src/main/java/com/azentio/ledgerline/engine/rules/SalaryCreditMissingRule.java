package com.azentio.ledgerline.engine.rules;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.domain.SalarySlip;
import com.azentio.ledgerline.domain.Transaction;
import com.azentio.ledgerline.engine.NameNormalizer;
import com.azentio.ledgerline.engine.Rule;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every salary slip must be backed by a matching employer credit on the statement. */
public class SalaryCreditMissingRule implements Rule {

    public static final String ID = "SALARY_CREDIT_MISSING";

    private final RulesConfig.SalaryCreditMissing config;

    public SalaryCreditMissingRule(RulesConfig config) {
        this.config = config.getSalaryCreditMissing();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(LoanFile file) {
        List<String> unmatched = file.salarySlips().stream()
                .filter(slip -> !hasMatchingCredit(slip, file.bankStatement().transactions()))
                .map(SalarySlip::month)
                .distinct()
                .sorted()
                .toList();
        if (unmatched.isEmpty()) {
            return Optional.empty();
        }
        String reason = "No salary credit from the slip employer within %s of net pay for month(s) %s"
                .formatted(percent(config.getAmountTolerance()), String.join(", ", unmatched));
        return Optional.of(new RuleHit(ID, config.getPoints(), reason, Map.of("unmatchedMonths", unmatched)));
    }

    private boolean hasMatchingCredit(SalarySlip slip, List<Transaction> transactions) {
        YearMonth month = YearMonth.parse(slip.month());
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(config.getGraceDays());
        BigDecimal allowed = slip.netPay().multiply(config.getAmountTolerance());
        return transactions.stream()
                .filter(Transaction::isCredit)
                .filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to))
                .filter(t -> t.amount().subtract(slip.netPay()).abs().compareTo(allowed) <= 0)
                .anyMatch(t -> NameNormalizer.narrationContainsEmployer(t.narration(), slip.employerName()));
    }

    private static String percent(BigDecimal fraction) {
        return fraction.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
