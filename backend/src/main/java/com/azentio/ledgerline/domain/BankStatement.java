package com.azentio.ledgerline.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record BankStatement(
        @NotBlank String docId,
        @NotBlank String accountNumber,
        @NotBlank String holderName,
        @NotNull BigDecimal openingBalance,
        @NotEmpty List<@NotNull @Valid Transaction> transactions,
        @NotNull @Valid DocMeta meta) {
}
