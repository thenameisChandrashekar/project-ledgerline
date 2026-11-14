package com.azentio.ledgerline.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;

/** Deliberately exposes only append and read: there is no update or delete path for audit rows. */
public interface CaseAuditRepository extends Repository<CaseAuditEntity, Long> {

    CaseAuditEntity save(CaseAuditEntity entry);

    List<CaseAuditEntity> findByCaseIdOrderByAtAscIdAsc(String caseId);
}
