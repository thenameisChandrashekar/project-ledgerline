package com.azentio.ledgerline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, String> {

    List<CaseEntity> findByStatusOrderByScoreDescCreatedAtAsc(CaseStatus status);

    List<CaseEntity> findAllByOrderByScoreDescCreatedAtAsc();

    Optional<CaseEntity> findByLoanFileId(Long loanFileId);
}
