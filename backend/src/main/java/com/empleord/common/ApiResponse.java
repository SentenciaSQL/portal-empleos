package com.empleord.common;

import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(boolean success, T data, String message, List<FieldError> errors, Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, List.of(), Instant.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, null, Texts.of(message), List.of(), Instant.now());
    }

    public static ApiResponse<Void> error(String message, List<FieldError> errors) {
        List<FieldError> translated = errors == null ? List.of() : errors.stream()
                .map(error -> new FieldError(error.field(), Texts.of(error.message())))
                .toList();
        return new ApiResponse<>(false, null, Texts.of(message), translated, Instant.now());
    }
}
