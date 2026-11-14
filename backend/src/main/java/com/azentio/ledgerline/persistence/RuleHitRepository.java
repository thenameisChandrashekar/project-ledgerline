package com.azentio.ledgerline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleHitRepository extends JpaRepository<RuleHitEntity, Long> {

    List<RuleHitEntity> findByLoanFileIdOrderById(Long loanFileId);
}
