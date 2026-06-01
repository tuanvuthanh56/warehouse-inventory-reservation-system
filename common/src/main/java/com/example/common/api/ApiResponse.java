package com.example.common.api;

/**
 * Shared API envelope required for both successful and failed responses.
 */
public record ApiResponse<T>(

        T data,

        ApiErrorResponse error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> failure(ApiErrorResponse error) {
        return new ApiResponse<>(null, error);
    }
}
