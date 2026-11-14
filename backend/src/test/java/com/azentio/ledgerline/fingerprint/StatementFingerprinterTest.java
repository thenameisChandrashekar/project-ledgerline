package com.azentio.ledgerline.fingerprint;

import com.azentio.ledgerline.domain.BankStatement;
import com.azentio.ledgerline.domain.Transaction;
import com.azentio.ledgerline.engine.TestLoanFiles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementFingerprinterTest {

    private final BankStatement original = TestLoanFiles.clean().bankStatement();

    @Test
    void changingOnlyHolderAndAccountKeepsTemplateButChangesContent() {
        BankStatement renamed = new BankStatement("OTHER-DOC", "999988887777", "Ravi Kumar", original.openingBalance(),
                original.transactions(), TestLoanFiles.cleanMeta());
        assertThat(StatementFingerprinter.templateHash(renamed)).isEqualTo(StatementFingerprinter.templateHash(original));
        assertThat(StatementFingerprinter.contentHash(renamed)).isNotEqualTo(StatementFingerprinter.contentHash(original));
    }

    @Test
    void changingOneTransactionAmountChangesBothHashes() {
        List<Transaction> txns = new ArrayList<>(original.transactions());
        Transaction t = txns.get(3);
        txns.set(3, new Transaction(t.date(), t.narration(), t.type(), t.amount().add(BigDecimal.ONE), t.balance()));
        BankStatement edited = new BankStatement(original.docId(), original.accountNumber(), original.holderName(),
                original.openingBalance(), txns, original.meta());
        assertThat(StatementFingerprinter.templateHash(edited)).isNotEqualTo(StatementFingerprinter.templateHash(original));
        assertThat(StatementFingerprinter.contentHash(edited)).isNotEqualTo(StatementFingerprinter.contentHash(original));
    }

    @Test
    void numberScaleAndDocIdDoNotAffectTheHash() {
        BankStatement rescaled = new BankStatement("NEW-DOC-ID", original.accountNumber(), original.holderName(),
                new BigDecimal("10000"), original.transactions(), TestLoanFiles.cleanMeta());
        assertThat(StatementFingerprinter.contentHash(rescaled)).isEqualTo(StatementFingerprinter.contentHash(original));
    }
}
