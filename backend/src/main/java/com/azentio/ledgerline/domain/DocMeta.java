package com.azentio.ledgerline.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** Metadata of the source PDF the contents were extracted from. */
public record DocMeta(
        String producer,
        @NotNull Instant createdAt,
        @NotNull Instant modifiedAt,
        @NotNull @PositiveOrZero Integer incrementalUpdates) {
}
