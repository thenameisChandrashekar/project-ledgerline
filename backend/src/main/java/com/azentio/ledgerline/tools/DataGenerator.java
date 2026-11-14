package com.azentio.ledgerline.tools;

import com.azentio.ledgerline.domain.Applicant;
import com.azentio.ledgerline.domain.BankStatement;
import com.azentio.ledgerline.domain.DocMeta;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.domain.SalarySlip;
import com.azentio.ledgerline.domain.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic generator of synthetic loan files. Every value is invented: names, PANs, account numbers and
 * employers come from small fictional pools combined at random.
 */
public class DataGenerator {

    public record GeneratedFile(LoanFile file, SeededPattern pattern) {
    }

    private record RawTxn(LocalDate date, String narration, String type, BigDecimal amount) {
    }

    private static final String[] FIRST = {"Asha", "Ravi", "Meera", "Arjun", "Kavya", "Vikram", "Priya", "Rahul",
            "Sneha", "Karthik", "Divya", "Nikhil", "Pooja", "Suresh", "Anjali", "Rohan", "Lakshmi", "Manoj", "Neha", "Aditya"};
    private static final String[] LAST = {"Rao", "Sharma", "Iyer", "Nair", "Reddy", "Gupta", "Menon", "Patel", "Das",
            "Kulkarni", "Joshi", "Pillai", "Verma", "Hegde", "Shetty", "Bose", "Mishra", "Naidu"};
    private static final String[] EMPLOYERS = {"Northwind Analytics Pvt. Ltd.", "Contoso Trading Private Limited",
            "Bluefin Logistics Ltd", "Silverleaf Software Pvt Ltd", "Orchid Healthcare Limited",
            "Tidewater Engineering Pvt. Ltd.", "Granite Retail Private Ltd", "Lumen Fintech Ltd."};
    private static final String[] EMPLOYER_SUFFIX_IN_NARRATION = {" PRIVATE LIMITED", " PVT LTD", " LTD", "", " PVT. LTD."};
    private static final String[] SAFE_PRODUCERS = {"Oracle BI Publisher 12.2", "JasperReports Library 6.20",
            "Finacle eStatement Engine", "Microsoft SQL Server Reporting Services", "iText 7.2 Payroll"};
    private static final String[] EDITING_PRODUCERS = {"Adobe Acrobat Pro 2024", "Wondershare PDFelement 10",
            "iLovePDF", "Sejda PDF Desktop", "Foxit PhantomPDF 12"};
    private static final String[] MERCHANTS = {"UPI/SWIGGY/FOOD", "UPI/ZOMATO", "POS/RELIANCE FRESH", "POS/DMART",
            "UPI/BESCOM ELECTRICITY", "ACH/ICICI CARD PAYMENT", "ATM WDL/MG ROAD", "UPI/UBER INDIA", "POS/SHELL FUEL",
            "UPI/AIRTEL POSTPAID", "NEFT/RENT TO LANDLORD", "UPI/AMAZON PAY", "POS/APOLLO PHARMACY"};
    private static final YearMonth LAST_SLIP_MONTH = YearMonth.of(2026, 9);
    private static final LocalDate STATEMENT_END = LocalDate.of(2026, 10, 10);

    private final Random random;
    private final String fileIdPrefix;
    private final Instant submittedAt;
    private int sequence;

    public DataGenerator(long seed, String fileIdPrefix, Instant submittedAt) {
        this.random = new Random(seed);
        this.fileIdPrefix = fileIdPrefix;
        this.submittedAt = submittedAt;
    }

    /** Generates {@code total} files: 60% CLEAN, the rest spread evenly over the fraud patterns, shuffled. */
    public List<GeneratedFile> generate(int total) {
        List<SeededPattern> frauds = List.of(SeededPattern.TAMPERED_BALANCE, SeededPattern.FABRICATED_SLIP,
                SeededPattern.INFLATED_DECLARATION, SeededPattern.EDITED_METADATA, SeededPattern.NAME_SWAP);
        int cleanCount = (int) Math.ceil(total * 0.6);
        List<SeededPattern> plan = new ArrayList<>(Collections.nCopies(cleanCount, SeededPattern.CLEAN));
        for (int i = 0; plan.size() < total; i++) {
            plan.add(frauds.get(i % frauds.size()));
        }
        Collections.shuffle(plan, random);
        List<GeneratedFile> files = new ArrayList<>();
        for (SeededPattern pattern : plan) {
            files.add(new GeneratedFile(build(pattern), pattern));
        }
        return files;
    }

    /**
     * Generates {@code total} files of which {@code recycled} reuse the statement of an earlier CLEAN file. Recycled
     * files are spread through the sequence and always come at least 20 positions after their source.
     */
    public List<GeneratedFile> generate(int total, int recycled) {
        List<GeneratedFile> files = new ArrayList<>(generate(total - recycled));
        int firstSlot = Math.min(60, files.size());
        int stride = Math.max(1, (files.size() - firstSlot) / Math.max(1, recycled));
        for (int i = 0; i < recycled; i++) {
            int position = Math.min(files.size(), firstSlot + i * stride + i);
            List<GeneratedFile> candidates = files.subList(0, Math.max(1, position - 20)).stream()
                    .filter(f -> f.pattern() == SeededPattern.CLEAN)
                    .toList();
            files.add(position, recycle(candidates.get(random.nextInt(candidates.size())).file()));
        }
        return files;
    }

    private LoanFile build(SeededPattern pattern) {
        String fileId = "%s-%05d".formatted(fileIdPrefix, ++sequence);
        String name = pick(FIRST) + " " + pick(LAST);
        String employer = pick(EMPLOYERS);
        BigDecimal net = money(30_000 + random.nextInt(220_000));
        int months = 3 + random.nextInt(4);

        List<SalarySlip> slips = new ArrayList<>();
        List<RawTxn> raw = new ArrayList<>();
        String narrationEmployer = employer.replaceAll("(?i)\\s*(pvt\\.?|private)?\\s*(ltd\\.?|limited)\\.?$", "")
                .toUpperCase(Locale.ROOT) + pick(EMPLOYER_SUFFIX_IN_NARRATION);
        int narrationStyle = random.nextInt(5);
        for (int m = months - 1; m >= 0; m--) {
            YearMonth month = LAST_SLIP_MONTH.minusMonths(m);
            BigDecimal slipNet = net.add(money(random.nextInt(2001) - 1000)); // small month-to-month variation
            slips.add(new SalarySlip(fileId + "-SLIP-" + month, month.toString(), variant(name), employer,
                    slipNet.multiply(BigDecimal.valueOf(1.15 + random.nextDouble() * 0.2)).setScale(2, RoundingMode.HALF_UP),
                    slipNet, cleanMeta(month.plusMonths(1).atDay(1))));
            // Salary lands on the 1st-5th of the next month, up to 1% off the slip net pay.
            BigDecimal credited = slipNet.multiply(BigDecimal.valueOf(1 + (random.nextDouble() * 0.02 - 0.01)))
                    .setScale(2, RoundingMode.HALF_UP);
            raw.add(new RawTxn(month.plusMonths(1).atDay(1 + random.nextInt(5)),
                    narration(narrationStyle, narrationEmployer, month), "CR", credited));
            addEverydaySpending(raw, month, slipNet);
        }
        BigDecimal declared = average(slips).multiply(BigDecimal.valueOf(0.95 + random.nextDouble() * 0.2))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal opening = net.add(money(20_000 + random.nextInt(180_000)));
        String holder = random.nextBoolean() ? name.toUpperCase(Locale.ROOT) : name;
        DocMeta statementMeta = cleanMeta(STATEMENT_END.plusDays(1));

        switch (pattern) {
            case FABRICATED_SLIP -> {
                // Slips name an employer that never paid the applicant; the real salary comes from elsewhere.
                String fake = otherEmployer(employer);
                slips = slips.stream().map(s -> new SalarySlip(s.docId(), s.month(), s.employeeName(), fake,
                        s.grossPay(), s.netPay(), s.meta())).toList();
                employer = fake;
            }
            case INFLATED_DECLARATION -> declared = average(slips)
                    .multiply(BigDecimal.valueOf(1.3 + random.nextDouble() * 0.7)).setScale(0, RoundingMode.HALF_UP);
            case EDITED_METADATA -> statementMeta = editedMeta(STATEMENT_END.plusDays(1));
            case NAME_SWAP -> holder = otherName(name);
            default -> {
                // CLEAN and TAMPERED_BALANCE (applied after balances are computed)
            }
        }

        List<Transaction> transactions = withBalances(raw, opening);
        if (pattern == SeededPattern.TAMPERED_BALANCE) {
            transactions = liftBalances(transactions);
        }
        BankStatement statement = new BankStatement(fileId + "-STMT", accountNumber(), holder, opening, transactions,
                statementMeta);
        return new LoanFile(fileId, submittedAt, new Applicant(name, pan()), declared, employer, slips, statement);
    }

    /** Fraud ring reuse: same statement ledger and slips as {@code source}, new applicant, PAN and account. */
    public GeneratedFile recycle(LoanFile source) {
        String fileId = "%s-%05d".formatted(fileIdPrefix, ++sequence);
        String name = otherName(source.applicant().name());
        List<SalarySlip> slips = source.salarySlips().stream()
                .map(s -> new SalarySlip(fileId + "-SLIP-" + s.month(), s.month(), name, s.employerName(), s.grossPay(),
                        s.netPay(), s.meta()))
                .toList();
        BankStatement s = source.bankStatement();
        BankStatement statement = new BankStatement(fileId + "-STMT", accountNumber(), name, s.openingBalance(),
                s.transactions(), s.meta());
        return new GeneratedFile(new LoanFile(fileId, submittedAt, new Applicant(name, pan()),
                source.declaredMonthlyIncome(), source.employerName(), slips, statement), SeededPattern.RECYCLED_DOCUMENT);
    }

    private void addEverydaySpending(List<RawTxn> raw, YearMonth month, BigDecimal net) {
        int count = 8 + random.nextInt(14);
        double budget = net.doubleValue() * (0.5 + random.nextDouble() * 0.3);
        for (int i = 0; i < count; i++) {
            LocalDate date = month.atDay(1 + random.nextInt(month.lengthOfMonth()));
            if (random.nextInt(10) == 0) {
                raw.add(new RawTxn(date, "UPI REFUND/AMAZON", "CR", money(100 + random.nextInt(3000))));
            } else {
                raw.add(new RawTxn(date, pick(MERCHANTS), "DR",
                        BigDecimal.valueOf(budget / count * (0.3 + random.nextDouble() * 1.4)).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        if (random.nextBoolean()) {
            raw.add(new RawTxn(month.atEndOfMonth(), "INT.PD:SB INTEREST", "CR", money(50 + random.nextInt(400))));
        }
    }

    private static List<Transaction> withBalances(List<RawTxn> raw, BigDecimal opening) {
        List<RawTxn> sorted = raw.stream()
                .filter(t -> !t.date().isAfter(STATEMENT_END))
                .sorted(Comparator.comparing(RawTxn::date))
                .toList();
        List<Transaction> result = new ArrayList<>();
        BigDecimal balance = opening;
        for (RawTxn t : sorted) {
            balance = t.type().equals("CR") ? balance.add(t.amount()) : balance.subtract(t.amount());
            result.add(new Transaction(t.date(), t.narration(), t.type(), t.amount(), balance));
        }
        return result;
    }

    /** Edits one balance in the middle and carries the lift forward, as a forger inflating the balance would. */
    private List<Transaction> liftBalances(List<Transaction> txns) {
        int from = txns.size() / 3 + random.nextInt(Math.max(1, txns.size() / 3));
        BigDecimal delta = money(5_000 + random.nextInt(95_000));
        List<Transaction> result = new ArrayList<>(txns.subList(0, from));
        for (Transaction t : txns.subList(from, txns.size())) {
            result.add(new Transaction(t.date(), t.narration(), t.type(), t.amount(), t.balance().add(delta)));
        }
        return result;
    }

    private String narration(int style, String employer, YearMonth month) {
        String mon = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        return switch (style) {
            case 0 -> "NEFT/" + employer + "/SAL " + mon;
            case 1 -> "BY TRANSFER-NEFT*" + employer + "*SALARY " + mon;
            case 2 -> "IMPS-" + employer + "-SALARY";
            case 3 -> "SALARY CREDIT " + employer + " " + mon + month.getYear() % 100;
            default -> "ACH CR " + employer + " PAYROLL";
        };
    }

    private DocMeta cleanMeta(LocalDate day) {
        Instant created = day.atTime(6, random.nextInt(60)).toInstant(ZoneOffset.UTC);
        return new DocMeta(pick(SAFE_PRODUCERS), created, created.plusSeconds(random.nextInt(600)), 0);
    }

    private DocMeta editedMeta(LocalDate day) {
        Instant created = day.atTime(6, 0).toInstant(ZoneOffset.UTC);
        return switch (random.nextInt(3)) {
            case 0 -> new DocMeta(pick(EDITING_PRODUCERS), created, created.plusSeconds(300), 0);
            case 1 -> new DocMeta(pick(SAFE_PRODUCERS), created, created.plusSeconds(120), 1 + random.nextInt(3));
            default -> new DocMeta(pick(SAFE_PRODUCERS), created, created.plusSeconds(86_400L * (2 + random.nextInt(20))), 0);
        };
    }

    /** Same name as the applicant apart from case and spacing. */
    private String variant(String name) {
        return switch (random.nextInt(3)) {
            case 0 -> name.toUpperCase(Locale.ROOT);
            case 1 -> name.replace(" ", "  ");
            default -> name;
        };
    }

    private String otherName(String name) {
        String candidate;
        do {
            candidate = pick(FIRST) + " " + pick(LAST);
        } while (candidate.equalsIgnoreCase(name));
        return candidate;
    }

    private String otherEmployer(String employer) {
        String candidate;
        do {
            candidate = pick(EMPLOYERS);
        } while (candidate.equals(employer));
        return candidate;
    }

    private String pan() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('A' + random.nextInt(26)));
        }
        sb.append('P').append((char) ('A' + random.nextInt(26)));
        sb.append("%04d".formatted(random.nextInt(10_000)));
        return sb.append((char) ('A' + random.nextInt(26))).toString();
    }

    private String accountNumber() {
        return "%012d".formatted((long) (random.nextDouble() * 1_000_000_000_000L));
    }

    private static BigDecimal average(List<SalarySlip> slips) {
        return slips.stream().map(SalarySlip::netPay).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(slips.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(long rupees) {
        return BigDecimal.valueOf(rupees).setScale(2, RoundingMode.UNNECESSARY);
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
