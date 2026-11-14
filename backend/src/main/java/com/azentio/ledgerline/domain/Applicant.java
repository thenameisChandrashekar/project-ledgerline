package com.azentio.ledgerline.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record Applicant(
        @NotBlank String name,
        @NotNull @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "must match [A-Z]{5}[0-9]{4}[A-Z]") String pan) {
}
