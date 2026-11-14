package com.azentio.ledgerline.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        @NotNull LocalDate date,
        @NotBlank String narration,
        @NotNull @Pattern(regexp = "CR|DR", message = "must be CR or DR") String type,
        @NotNull @Positive BigDecimal amount,
        @NotNull BigDecimal balance) {

    public boolean isCredit() {
        return "CR".equals(type);
    }
}
