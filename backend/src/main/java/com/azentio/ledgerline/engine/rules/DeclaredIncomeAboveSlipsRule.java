package com.azentio.ledgerline.engine.rules;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.domain.SalarySlip;
import com.azentio.ledgerline.engine.Rule;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Declared take-home pay must not exceed the slips' average net pay by more than the allowed ratio. */
public class DeclaredIncomeAboveSlipsRule implements Rule {

    public static final String ID = "DECLARED_INCOME_ABOVE_SLIPS";

    private final RulesConfig.DeclaredIncome config;

    public DeclaredIncomeAboveSlipsRule(RulesConfig config) {
        this.config = config.getDeclaredIncome();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(LoanFile file) {
        BigDecimal total = file.salarySlips().stream().map(SalarySlip::netPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(file.salarySlips().size()), 2, RoundingMode.HALF_UP);
        BigDecimal limit = average.multiply(config.getMaxRatio());
        if (file.declaredMonthlyIncome().compareTo(limit) <= 0) {
            return Optional.empty();
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("declaredMonthlyIncome", file.declaredMonthlyIncome());
        evidence.put("averageSlipNetPay", average);
        evidence.put("maxRatio", config.getMaxRatio());
        String reason = "Declared monthly income %s is more than %sx the average slip net pay %s"
                .formatted(file.declaredMonthlyIncome().toPlainString(), config.getMaxRatio().toPlainString(),
                        average.toPlainString());
        return Optional.of(new RuleHit(ID, config.getPoints(), reason, evidence));
    }
}
