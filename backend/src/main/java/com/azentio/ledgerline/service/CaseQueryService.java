package com.azentio.ledgerline.service;

import com.azentio.ledgerline.api.CaseDetail;
import com.azentio.ledgerline.api.CaseSummary;
import com.azentio.ledgerline.persistence.CaseEntity;
import com.azentio.ledgerline.persistence.CaseRepository;
import com.azentio.ledgerline.persistence.CaseStatus;
import com.azentio.ledgerline.persistence.LoanFileEntity;
import com.azentio.ledgerline.persistence.LoanFileRepository;
import com.azentio.ledgerline.persistence.RuleHitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CaseQueryService {

    private final CaseRepository cases;
    private final LoanFileRepository loanFiles;
    private final RuleHitRepository ruleHits;
    private final ResultMapper mapper;

    public CaseQueryService(CaseRepository cases, LoanFileRepository loanFiles, RuleHitRepository ruleHits,
                            ResultMapper mapper) {
        this.cases = cases;
        this.loanFiles = loanFiles;
        this.ruleHits = ruleHits;
        this.mapper = mapper;
    }

    public List<CaseSummary> list(CaseStatus status) {
        List<CaseEntity> found = status == null
                ? cases.findAllByOrderByScoreDescCreatedAtAsc()
                : cases.findByStatusOrderByScoreDescCreatedAtAsc(status);
        Map<Long, LoanFileEntity> files = loanFiles.findAllById(found.stream().map(CaseEntity::getLoanFileId).toList())
                .stream().collect(Collectors.toMap(LoanFileEntity::getId, Function.identity()));
        return found.stream().map(c -> mapper.toSummary(c, files.get(c.getLoanFileId()))).toList();
    }

    public CaseDetail get(String caseId) {
        CaseEntity c = cases.findById(caseId).orElseThrow(() -> new NotFoundException("Case " + caseId + " not found"));
        LoanFileEntity file = loanFiles.findById(c.getLoanFileId()).orElseThrow();
        return mapper.toDetail(c, file, ruleHits.findByLoanFileIdOrderById(file.getId()));
    }
}
