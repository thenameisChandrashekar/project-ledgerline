package com.azentio.ledgerline.service;

import com.azentio.ledgerline.config.FingerprintProperties;
import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.Band;
import com.azentio.ledgerline.engine.RiskBander;
import com.azentio.ledgerline.engine.RuleEngine;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.fingerprint.DocumentReuseChecker;
import com.azentio.ledgerline.fingerprint.StatementFingerprinter;
import com.azentio.ledgerline.masking.Masking;
import com.azentio.ledgerline.persistence.CaseEntity;
import com.azentio.ledgerline.persistence.CaseRepository;
import com.azentio.ledgerline.persistence.LoanFileEntity;
import com.azentio.ledgerline.persistence.LoanFileRepository;
import com.azentio.ledgerline.persistence.RuleHitEntity;
import com.azentio.ledgerline.persistence.RuleHitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Evaluates a loan file, persists it with its rule hits and opens a case for REFER/HIGH.
 * Idempotent per fileId: the same payload replays the stored result; a different payload is a conflict.
 */
@Service
public class LoanFileService {

    private static final Logger log = LoggerFactory.getLogger(LoanFileService.class);

    private record ReuseOutcome(Optional<RuleHit> hit, boolean degraded) {
    }

    private final RuleEngine ruleEngine;
    private final RiskBander bander;
    private final DocumentReuseChecker reuseChecker;
    private final ExecutorService fingerprintExecutor;
    private final FingerprintProperties fingerprintProperties;
    private final LoanFileRepository loanFiles;
    private final RuleHitRepository ruleHits;
    private final CaseRepository cases;
    private final ResultMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public LoanFileService(RuleEngine ruleEngine, RiskBander bander, DocumentReuseChecker reuseChecker,
                           ExecutorService fingerprintExecutor, FingerprintProperties fingerprintProperties,
                           LoanFileRepository loanFiles, RuleHitRepository ruleHits, CaseRepository cases,
                           ResultMapper mapper, TransactionTemplate tx, Clock clock) {
        this.ruleEngine = ruleEngine;
        this.bander = bander;
        this.reuseChecker = reuseChecker;
        this.fingerprintExecutor = fingerprintExecutor;
        this.fingerprintProperties = fingerprintProperties;
        this.loanFiles = loanFiles;
        this.ruleHits = ruleHits;
        this.cases = cases;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
    }

    public Submission submit(LoanFile file) {
        String payload = mapper.toJson(file);
        String payloadHash = StatementFingerprinter.sha256(payload);
        Optional<LoanFileEntity> existing = loanFiles.findByFileId(file.fileId());
        if (existing.isPresent()) {
            return replay(existing.get(), payloadHash);
        }

        List<RuleHit> hits = new ArrayList<>(ruleEngine.evaluate(file));
        ReuseOutcome reuse = checkReuse(file);
        reuse.hit().ifPresent(hits::add);
        List<String> degradedSignals = reuse.degraded() ? List.of(DocumentReuseChecker.ID) : List.of();
        int score = hits.stream().mapToInt(RuleHit::points).sum();
        Band band = bander.band(score);
        if (reuse.degraded() && band == Band.LOW) {
            band = Band.REFER; // a file we could not fully check is never silently treated as clean
        }

        Submission submission;
        try {
            submission = persist(file, payload, payloadHash, hits, score, band, degradedSignals);
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same fileId won the unique constraint.
            return replay(loanFiles.findByFileId(file.fileId()).orElseThrow(() -> race), payloadHash);
        }
        if (!reuse.degraded()) {
            recordFingerprints(file);
        }
        log.info("Evaluated loan file {} pan={} account={} score={} band={} rules={} degraded={}", file.fileId(),
                Masking.pan(file.applicant().pan()), Masking.account(file.bankStatement().accountNumber()), score, band,
                hits.stream().map(RuleHit::ruleId).toList(), degradedSignals);
        return submission;
    }

    private Submission persist(LoanFile file, String payload, String payloadHash, List<RuleHit> hits, int score,
                               Band band, List<String> degradedSignals) {
        Instant now = clock.instant();
        return tx.execute(status -> {
            LoanFileEntity saved = loanFiles.saveAndFlush(new LoanFileEntity(file.fileId(), payloadHash, payload,
                    file.applicant().name(), file.applicant().pan(), file.bankStatement().accountNumber(),
                    file.submittedAt(), score, band.name(),
                    degradedSignals.isEmpty() ? null : String.join(",", degradedSignals), now));
            List<RuleHitEntity> savedHits = ruleHits.saveAll(hits.stream()
                    .map(h -> new RuleHitEntity(saved.getId(), h.ruleId(), h.points(), h.reason(), mapper.toJson(h.evidence())))
                    .toList());
            CaseEntity caseEntity = band.opensCase()
                    ? cases.save(new CaseEntity(UUID.randomUUID().toString(), saved.getId(), file.fileId(), score,
                    band.name(), now))
                    : null;
            return new Submission(mapper.toResult(saved, savedHits, caseEntity), true);
        });
    }

    private Submission replay(LoanFileEntity stored, String payloadHash) {
        if (!stored.getPayloadHash().equals(payloadHash)) {
            throw new ConflictException("fileId " + stored.getFileId() + " was already submitted with a different payload");
        }
        return new Submission(mapper.toResult(stored, ruleHits.findByLoanFileIdOrderById(stored.getId()),
                cases.findByLoanFileId(stored.getId()).orElse(null)), false);
    }

    private ReuseOutcome checkReuse(LoanFile file) {
        try {
            return new ReuseOutcome(withTimeout(() -> reuseChecker.check(file)), false);
        } catch (Exception e) {
            log.warn("Fingerprint store unavailable for loan file {} pan={}; DOCUMENT_REUSED degraded: {}",
                    file.fileId(), Masking.pan(file.applicant().pan()), e.toString());
            return new ReuseOutcome(Optional.empty(), true);
        }
    }

    private void recordFingerprints(LoanFile file) {
        try {
            withTimeout(() -> {
                reuseChecker.record(file);
                return null;
            });
        } catch (Exception e) {
            log.warn("Could not record fingerprints for loan file {} pan={}: {}", file.fileId(),
                    Masking.pan(file.applicant().pan()), e.toString());
        }
    }

    private <T> T withTimeout(Callable<T> call) throws Exception {
        Future<T> future = fingerprintExecutor.submit(call);
        try {
            return future.get(fingerprintProperties.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
