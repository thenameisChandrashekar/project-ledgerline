package com.azentio.ledgerline.api;

import com.azentio.ledgerline.service.ConflictException;
import com.azentio.ledgerline.service.NotFoundException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();
        return new ApiError("VALIDATION_FAILED", "Request has invalid fields", fields);
    }

    /** Malformed JSON or a value of the wrong shape (for example an unparsable date). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining(".")).replace(".[", "[");
            String message = mismatch instanceof InvalidFormatException ? "has an invalid format" : "has the wrong type";
            return new ApiError("VALIDATION_FAILED", "Request has invalid fields",
                    List.of(new ApiError.FieldError(field, message)));
        }
        return ApiError.of("MALFORMED_REQUEST", "Request body is not valid JSON");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError missingHeader(MissingRequestHeaderException ex) {
        return ApiError.of("MISSING_HEADER", "Header " + ex.getHeaderName() + " is required");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(NotFoundException ex) {
        return ApiError.of("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(ConflictException ex) {
        return ApiError.of("CONFLICT", ex.getMessage());
    }
}
