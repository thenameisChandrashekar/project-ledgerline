package com.azentio.ledgerline.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DispositionRequest(
        @NotNull @Pattern(regexp = "CLEARED|REFER_FIELD_VERIFICATION|FRAUD_CONFIRMED",
                message = "must be CLEARED, REFER_FIELD_VERIFICATION or FRAUD_CONFIRMED") String disposition,
        @NotBlank @Size(min = 10, max = 2000, message = "must be at least 10 characters") String comment) {
}
