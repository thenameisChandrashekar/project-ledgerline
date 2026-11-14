package com.azentio.ledgerline.tools;

import com.azentio.ledgerline.engine.rules.BalanceBreakRule;
import com.azentio.ledgerline.engine.rules.DeclaredIncomeAboveSlipsRule;
import com.azentio.ledgerline.engine.rules.DocMetadataSuspectRule;
import com.azentio.ledgerline.engine.rules.NameMismatchRule;
import com.azentio.ledgerline.engine.rules.SalaryCreditMissingRule;
import com.azentio.ledgerline.fingerprint.DocumentReuseChecker;

/** Label written to the sidecar file for every generated loan file, with the rule it is expected to fire. */
public enum SeededPattern {
    CLEAN(null),
    TAMPERED_BALANCE(BalanceBreakRule.ID),
    FABRICATED_SLIP(SalaryCreditMissingRule.ID),
    INFLATED_DECLARATION(DeclaredIncomeAboveSlipsRule.ID),
    EDITED_METADATA(DocMetadataSuspectRule.ID),
    NAME_SWAP(NameMismatchRule.ID),
    RECYCLED_DOCUMENT(DocumentReuseChecker.ID);

    private final String expectedRule;

    SeededPattern(String expectedRule) {
        this.expectedRule = expectedRule;
    }

    public String expectedRule() {
        return expectedRule;
    }
}
