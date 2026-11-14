package com.azentio.ledgerline.engine;

import com.azentio.ledgerline.domain.Applicant;
import com.azentio.ledgerline.domain.BankStatement;
import com.azentio.ledgerline.domain.DocMeta;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.domain.SalarySlip;
import com.azentio.ledgerline.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Hand-built clean loan file used as the baseline for rule tests. */
public final class TestLoanFiles {

    public static final String EMPLOYER = "Northwind Analytics Pvt. Ltd.";
    public static final Instant CREATED = Instant.parse("2026-10-10T09:00:00Z");

    private TestLoanFiles() {
    }

    public static DocMeta cleanMeta() {
        return new DocMeta("Tally Payroll 4.1", CREATED, CREATED.plusSeconds(60), 0);
    }

    public static SalarySlip slip(String month, String net) {
        return new SalarySlip("SLIP-" + month, month, "Asha Rao", EMPLOYER, new BigDecimal(net).add(new BigDecimal("8000")),
                new BigDecimal(net), cleanMeta());
    }

    public static Transaction txn(String date, String narration, String type, String amount, String balance) {
        return new Transaction(LocalDate.parse(date), narration, type, new BigDecimal(amount), new BigDecimal(balance));
    }

    /** Two months of slips (net 52,000) with salary credited early next month, plus everyday spending. */
    public static List<Transaction> cleanTransactions() {
        List<Transaction> t = new ArrayList<>();
        t.add(txn("2026-08-05", "UPI/SWIGGY/FOOD", "DR", "450.00", "9550.00"));
        t.add(txn("2026-09-02", "NEFT/NORTHWIND ANALYTICS PRIVATE LIMITED/SAL AUG", "CR", "52000.00", "61550.00"));
        t.add(txn("2026-09-10", "ACH/HDFC HOME LOAN EMI", "DR", "18000.00", "43550.00"));
        t.add(txn("2026-09-21", "POS/RELIANCE FRESH", "DR", "2310.50", "41239.50"));
        t.add(txn("2026-10-01", "NEFT/NORTHWIND ANALYTICS PRIVATE LIMITED/SAL SEP", "CR", "52000.00", "93239.50"));
        return t;
    }

    public static LoanFile clean() {
        return withTransactions(cleanTransactions());
    }

    public static LoanFile withTransactions(List<Transaction> transactions) {
        BankStatement statement = new BankStatement("STMT-1", "001234564821", "Asha Rao", new BigDecimal("10000.00"),
                transactions, cleanMeta());
        return new LoanFile("LF-TEST-1", Instant.parse("2026-10-12T10:00:00Z"), new Applicant("Asha Rao", "ABCDE1234F"),
                new BigDecimal("52000"), EMPLOYER, List.of(slip("2026-08", "52000.00"), slip("2026-09", "52000.00")),
                statement);
    }

    public static LoanFile withDeclared(LoanFile f, String declared) {
        return new LoanFile(f.fileId(), f.submittedAt(), f.applicant(), new BigDecimal(declared), f.employerName(),
                f.salarySlips(), f.bankStatement());
    }

    public static LoanFile withSlips(LoanFile f, List<SalarySlip> slips) {
        return new LoanFile(f.fileId(), f.submittedAt(), f.applicant(), f.declaredMonthlyIncome(), f.employerName(),
                slips, f.bankStatement());
    }

    public static LoanFile withStatementMeta(LoanFile f, DocMeta meta) {
        BankStatement s = f.bankStatement();
        return new LoanFile(f.fileId(), f.submittedAt(), f.applicant(), f.declaredMonthlyIncome(), f.employerName(),
                f.salarySlips(), new BankStatement(s.docId(), s.accountNumber(), s.holderName(), s.openingBalance(),
                s.transactions(), meta));
    }

    public static LoanFile withHolder(LoanFile f, String holder) {
        BankStatement s = f.bankStatement();
        return new LoanFile(f.fileId(), f.submittedAt(), f.applicant(), f.declaredMonthlyIncome(), f.employerName(),
                f.salarySlips(), new BankStatement(s.docId(), s.accountNumber(), holder, s.openingBalance(),
                s.transactions(), s.meta()));
    }
}
