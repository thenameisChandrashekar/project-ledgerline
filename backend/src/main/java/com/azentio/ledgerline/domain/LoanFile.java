package com.azentio.ledgerline.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Extracted contents of one loan file, as received by POST /api/loan-files. */
public record LoanFile(
        @NotBlank String fileId,
        @NotNull Instant submittedAt,
        @NotNull @Valid Applicant applicant,
        @NotNull @Positive BigDecimal declaredMonthlyIncome,
        @NotBlank String employerName,
        @NotNull @Size(min = 1, max = 6) List<@NotNull @Valid SalarySlip> salarySlips,
        @NotNull @Valid BankStatement bankStatement) {
}
