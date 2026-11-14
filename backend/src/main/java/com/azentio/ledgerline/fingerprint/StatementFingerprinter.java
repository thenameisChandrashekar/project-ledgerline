package com.azentio.ledgerline.fingerprint;

import com.azentio.ledgerline.domain.BankStatement;
import com.azentio.ledgerline.domain.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Canonical form (one field per line, UTF-8, '\n' separated):
 * <pre>
 * holderName=&lt;upper-case, single-spaced&gt;     (content form only)
 * accountNumber=&lt;trimmed&gt;                     (content form only)
 * openingBalance=&lt;plain decimal, 2 places, HALF_UP&gt;
 * txn=&lt;yyyy-MM-dd&gt;|&lt;CR|DR&gt;|&lt;amount 2dp&gt;|&lt;balance 2dp&gt;|&lt;narration upper-case, single-spaced&gt;   (one per row, in order)
 * </pre>
 * docId and meta are never part of either form. The template form omits holderName and accountNumber.
 */
public final class StatementFingerprinter {

    private StatementFingerprinter() {
    }

    public static String contentHash(BankStatement statement) {
        return sha256("holderName=" + text(statement.holderName()) + "\n"
                + "accountNumber=" + statement.accountNumber().trim() + "\n"
                + templateForm(statement));
    }

    public static String templateHash(BankStatement statement) {
        return sha256(templateForm(statement));
    }

    static String templateForm(BankStatement statement) {
        StringBuilder sb = new StringBuilder("openingBalance=").append(amount(statement.openingBalance())).append('\n');
        for (Transaction t : statement.transactions()) {
            sb.append("txn=").append(t.date()).append('|').append(t.type()).append('|')
                    .append(amount(t.amount())).append('|').append(amount(t.balance())).append('|')
                    .append(text(t.narration())).append('\n');
        }
        return sb.toString();
    }

    private static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String text(String value) {
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
