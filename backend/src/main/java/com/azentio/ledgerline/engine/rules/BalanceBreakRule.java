package com.azentio.ledgerline.engine.rules;

import com.azentio.ledgerline.domain.BankStatement;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.domain.Transaction;
import com.azentio.ledgerline.engine.Rule;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Running balance must reconcile from the opening balance through every transaction. */
public class BalanceBreakRule implements Rule {

    public static final String ID = "BALANCE_BREAK";

    private final RulesConfig.BalanceBreak config;

    public BalanceBreakRule(RulesConfig config) {
        this.config = config.getBalanceBreak();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<RuleHit> evaluate(LoanFile file) {
        BankStatement statement = file.bankStatement();
        BigDecimal previous = statement.openingBalance();
        for (int i = 0; i < statement.transactions().size(); i++) {
            Transaction txn = statement.transactions().get(i);
            BigDecimal expected = txn.isCredit() ? previous.add(txn.amount()) : previous.subtract(txn.amount());
            if (expected.subtract(txn.balance()).abs().compareTo(config.getTolerance()) > 0) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("docId", statement.docId());
                evidence.put("rowIndex", i);
                evidence.put("date", txn.date().toString());
                evidence.put("expectedBalance", expected);
                evidence.put("statedBalance", txn.balance());
                String reason = "Statement balance breaks at row %d (%s): expected %s but statement shows %s"
                        .formatted(i, txn.date(), expected.toPlainString(), txn.balance().toPlainString());
                return Optional.of(new RuleHit(ID, config.getPoints(), reason, evidence));
            }
            // Continue from the stated balance so one edit reports only the first break.
            previous = txn.balance();
        }
        return Optional.empty();
    }
}
