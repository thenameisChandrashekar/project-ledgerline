package com.azentio.ledgerline.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String error, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, List.of());
    }
}
