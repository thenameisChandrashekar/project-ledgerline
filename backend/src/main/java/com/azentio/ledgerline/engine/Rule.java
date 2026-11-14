package com.azentio.ledgerline.engine;

import com.azentio.ledgerline.domain.LoanFile;

import java.util.Optional;

/** A single explainable consistency check. Implementations must be pure and Spring-free. */
public interface Rule {

    String id();

    Optional<RuleHit> evaluate(LoanFile file);
}
