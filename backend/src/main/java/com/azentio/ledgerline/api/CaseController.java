package com.azentio.ledgerline.api;

import com.azentio.ledgerline.persistence.CaseStatus;
import com.azentio.ledgerline.service.CaseQueryService;
import com.azentio.ledgerline.service.DispositionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseQueryService queries;
    private final DispositionService dispositions;

    public CaseController(CaseQueryService queries, DispositionService dispositions) {
        this.queries = queries;
        this.dispositions = dispositions;
    }

    /** Cases with the given status (all statuses when omitted), highest score first. */
    @GetMapping
    public List<CaseSummary> list(@RequestParam(required = false) CaseStatus status) {
        return queries.list(status);
    }

    @GetMapping("/{caseId}")
    public CaseDetail get(@PathVariable String caseId) {
        return queries.get(caseId);
    }

    /** Records an analyst decision; the acting user comes from the X-User header. Returns the refreshed case. */
    @PostMapping("/{caseId}/disposition")
    public CaseDetail dispose(@PathVariable String caseId, @RequestHeader("X-User") @NotBlank String user,
                              @Valid @RequestBody DispositionRequest request) {
        dispositions.dispose(caseId, CaseStatus.valueOf(request.disposition()), user.trim(), request.comment());
        return queries.get(caseId);
    }

    @GetMapping("/{caseId}/audit")
    public List<AuditEntryView> audit(@PathVariable String caseId) {
        return dispositions.history(caseId);
    }
}
