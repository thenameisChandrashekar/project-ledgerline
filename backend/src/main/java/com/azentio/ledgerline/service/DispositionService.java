package com.azentio.ledgerline.service;

import com.azentio.ledgerline.api.AuditEntryView;
import com.azentio.ledgerline.persistence.CaseAuditEntity;
import com.azentio.ledgerline.persistence.CaseAuditRepository;
import com.azentio.ledgerline.persistence.CaseEntity;
import com.azentio.ledgerline.persistence.CaseRepository;
import com.azentio.ledgerline.persistence.CaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies analyst dispositions under a fixed state machine and appends every change to case_audit. */
@Service
public class DispositionService {

    private static final Logger log = LoggerFactory.getLogger(DispositionService.class);

    static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = Map.of(
            CaseStatus.OPEN, EnumSet.of(CaseStatus.CLEARED, CaseStatus.REFER_FIELD_VERIFICATION, CaseStatus.FRAUD_CONFIRMED),
            CaseStatus.REFER_FIELD_VERIFICATION, EnumSet.of(CaseStatus.CLEARED, CaseStatus.FRAUD_CONFIRMED),
            CaseStatus.CLEARED, EnumSet.noneOf(CaseStatus.class),
            CaseStatus.FRAUD_CONFIRMED, EnumSet.noneOf(CaseStatus.class));

    private final CaseRepository cases;
    private final CaseAuditRepository audit;
    private final Clock clock;

    public DispositionService(CaseRepository cases, CaseAuditRepository audit, Clock clock) {
        this.cases = cases;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void dispose(String caseId, CaseStatus target, String actor, String comment) {
        CaseEntity c = cases.findById(caseId).orElseThrow(() -> new NotFoundException("Case " + caseId + " not found"));
        CaseStatus from = c.getStatus();
        if (!ALLOWED.get(from).contains(target)) {
            throw new ConflictException("Cannot move case from %s to %s".formatted(from, target));
        }
        Instant now = clock.instant();
        c.moveTo(target, now);
        cases.saveAndFlush(c); // @Version guards against two analysts acting at once
        audit.save(new CaseAuditEntity(caseId, from, target, actor, comment.trim(), now));
        log.info("Case {} moved {} -> {} by {}", caseId, from, target, actor);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryView> history(String caseId) {
        if (!cases.existsById(caseId)) {
            throw new NotFoundException("Case " + caseId + " not found");
        }
        return audit.findByCaseIdOrderByAtAscIdAsc(caseId).stream()
                .map(a -> new AuditEntryView(a.getCaseId(), a.getFromStatus().name(), a.getToStatus().name(),
                        a.getActor(), a.getComment(), a.getAt()))
                .toList();
    }
}
