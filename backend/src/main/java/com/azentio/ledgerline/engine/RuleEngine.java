package com.azentio.ledgerline.engine;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.rules.BalanceBreakRule;
import com.azentio.ledgerline.engine.rules.DeclaredIncomeAboveSlipsRule;
import com.azentio.ledgerline.engine.rules.DocMetadataSuspectRule;
import com.azentio.ledgerline.engine.rules.NameMismatchRule;
import com.azentio.ledgerline.engine.rules.SalaryCreditMissingRule;

import java.util.List;
import java.util.Optional;

/** Runs the stateless rules over a loan file. Cross-file signals are added by the service layer. */
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<RuleHit> evaluate(LoanFile file) {
        return rules.stream()
                .map(rule -> rule.evaluate(file))
                .flatMap(Optional::stream)
                .toList();
    }

    public static RuleEngine withDefaultRules(RulesConfig config) {
        return new RuleEngine(List.of(
                new BalanceBreakRule(config),
                new SalaryCreditMissingRule(config),
                new DeclaredIncomeAboveSlipsRule(config),
                new DocMetadataSuspectRule(config),
                new NameMismatchRule(config)));
    }
}
