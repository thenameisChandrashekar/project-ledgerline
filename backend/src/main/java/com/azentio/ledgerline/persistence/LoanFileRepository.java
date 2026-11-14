package com.azentio.ledgerline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanFileRepository extends JpaRepository<LoanFileEntity, Long> {

    Optional<LoanFileEntity> findByFileId(String fileId);
}
