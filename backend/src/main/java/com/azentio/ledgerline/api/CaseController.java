package com.azentio.ledgerline.api;

import com.azentio.ledgerline.persistence.CaseStatus;
import com.azentio.ledgerline.service.CaseQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseQueryService queries;

    public CaseController(CaseQueryService queries) {
        this.queries = queries;
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
}
