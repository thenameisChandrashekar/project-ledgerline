package com.azentio.ledgerline.fingerprint;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.engine.RuleHit;
import com.azentio.ledgerline.engine.RulesConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DOCUMENT_REUSED: the statement's template has been seen on another loan file with a different PAN inside the
 * configured window. Re-applications by the same PAN, and the file itself, never match.
 */
public class DocumentReuseChecker {

    public static final String ID = "DOCUMENT_REUSED";
    private static final int MAX_OTHER_FILES = 5;

    private final DocumentFingerprintStore store;
    private final Clock clock;
    private final RulesConfig.DocumentReused config;

    public DocumentReuseChecker(DocumentFingerprintStore store, Clock clock, RulesConfig config) {
        this.store = store;
        this.clock = clock;
        this.config = config.getDocumentReused();
    }

    public Optional<RuleHit> check(LoanFile file) {
        Instant notBefore = windowStart();
        String panDigest = StatementFingerprinter.sha256(file.applicant().pan());
        String contentHash = StatementFingerprinter.contentHash(file.bankStatement());
        String templateHash = StatementFingerprinter.templateHash(file.bankStatement());

        List<String> contentMatches = others(store.findSince(contentKey(contentHash), notBefore), file.fileId(), panDigest);
        List<String> templateMatches = others(store.findSince(templateKey(templateHash), notBefore), file.fileId(), panDigest);
        if (templateMatches.isEmpty()) {
            return Optional.empty();
        }
        boolean exactCopy = !contentMatches.isEmpty();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("docId", file.bankStatement().docId());
        evidence.put("matchedHash", exactCopy ? "contentHash" : "templateHash");
        evidence.put("otherFileIds", templateMatches.stream().limit(MAX_OTHER_FILES).toList());
        String reason = "Bank statement ledger matches %d other loan file(s) from a different applicant within %d days"
                .formatted(templateMatches.size(), config.getWindowDays());
        return Optional.of(new RuleHit(ID, config.getPoints(), reason, evidence));
    }

    public void record(LoanFile file) {
        Instant notBefore = windowStart();
        Duration ttl = Duration.ofDays(config.getWindowDays() + 1L);
        FingerprintEntry entry = new FingerprintEntry(file.fileId(), StatementFingerprinter.sha256(file.applicant().pan()),
                file.submittedAt());
        store.add(contentKey(StatementFingerprinter.contentHash(file.bankStatement())), entry, notBefore, ttl);
        store.add(templateKey(StatementFingerprinter.templateHash(file.bankStatement())), entry, notBefore, ttl);
    }

    private Instant windowStart() {
        return clock.instant().minus(Duration.ofDays(config.getWindowDays()));
    }

    private static List<String> others(List<FingerprintEntry> entries, String fileId, String panDigest) {
        return entries.stream()
                .filter(e -> !e.fileId().equals(fileId) && !e.panDigest().equals(panDigest))
                .map(FingerprintEntry::fileId)
                .distinct()
                .sorted()
                .toList();
    }

    static String contentKey(String hash) {
        return "ledgerline:fp:content:" + hash;
    }

    static String templateKey(String hash) {
        return "ledgerline:fp:template:" + hash;
    }
}
