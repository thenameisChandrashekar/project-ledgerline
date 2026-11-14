package com.azentio.ledgerline.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record SalarySlip(
        @NotBlank String docId,
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be YYYY-MM") String month,
        @NotBlank String employeeName,
        @NotBlank String employerName,
        @NotNull @PositiveOrZero BigDecimal grossPay,
        @NotNull @PositiveOrZero BigDecimal netPay,
        @NotNull @Valid DocMeta meta) {
}
