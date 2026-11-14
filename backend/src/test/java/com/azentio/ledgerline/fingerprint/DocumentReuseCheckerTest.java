package com.azentio.ledgerline.fingerprint;

import com.azentio.ledgerline.domain.Applicant;
import com.azentio.ledgerline.domain.BankStatement;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;
import com.azentio.ledgerline.engine.TestLoanFiles;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentReuseCheckerTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    /** Clock whose time the test moves forward. */
    static final class MutableClock extends Clock {
        Instant now = T0;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final DocumentReuseChecker checker =
            new DocumentReuseChecker(new InMemoryFingerprintStore(), clock, new RulesConfig());

    private static LoanFile original() {
        LoanFile f = TestLoanFiles.clean();
        return new LoanFile("LF-ORIG", T0, f.applicant(), f.declaredMonthlyIncome(), f.employerName(), f.salarySlips(),
                f.bankStatement());
    }

    /** Same statement ledger, new name, PAN and account number. */
    private static LoanFile recycled(Instant submittedAt, String pan) {
        LoanFile f = original();
        BankStatement s = f.bankStatement();
        return new LoanFile("LF-COPY", submittedAt, new Applicant("Ravi Kumar", pan), f.declaredMonthlyIncome(),
                f.employerName(), f.salarySlips(), new BankStatement("STMT-COPY", "999988887777", "Ravi Kumar",
                s.openingBalance(), s.transactions(), s.meta()));
    }

    @Test
    void firesInsideWindowForDifferentPan() {
        checker.record(original());
        clock.now = T0.plus(Duration.ofDays(100));
        RuleHit hit = checker.check(recycled(clock.now, "ZZZPK9999Z")).orElseThrow();
        assertThat(hit.points()).isEqualTo(60);
        assertThat(hit.evidence()).containsEntry("docId", "STMT-COPY")
                .containsEntry("matchedHash", "templateHash")
                .containsEntry("otherFileIds", List.of("LF-ORIG"));
    }

    @Test
    void doesNotFireWhenEarlierFileIsOlderThanWindow() {
        checker.record(original());
        clock.now = T0.plus(Duration.ofDays(181));
        assertThat(checker.check(recycled(clock.now, "ZZZPK9999Z"))).isEmpty();
    }

    @Test
    void doesNotFireForSamePanReapplication() {
        checker.record(original());
        clock.now = T0.plus(Duration.ofDays(10));
        assertThat(checker.check(recycled(clock.now, original().applicant().pan()))).isEmpty();
    }

    @Test
    void fileNeverMatchesItself() {
        checker.record(original());
        LoanFile sameFileOtherPan = new LoanFile("LF-ORIG", T0, new Applicant("X Y", "QQQPQ1111Q"),
                original().declaredMonthlyIncome(), original().employerName(), original().salarySlips(),
                original().bankStatement());
        assertThat(checker.check(sameFileOtherPan)).isEqualTo(Optional.empty());
    }

    @Test
    void exactCopyReportsContentHash() {
        checker.record(original());
        LoanFile f = original();
        LoanFile copy = new LoanFile("LF-EXACT", T0, new Applicant("Asha Rao", "MMMPM2222M"), f.declaredMonthlyIncome(),
                f.employerName(), f.salarySlips(), f.bankStatement());
        assertThat(checker.check(copy).orElseThrow().evidence()).containsEntry("matchedHash", "contentHash");
    }
}
