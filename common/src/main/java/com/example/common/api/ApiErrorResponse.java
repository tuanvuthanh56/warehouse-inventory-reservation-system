package com.example.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        String traceId,
        Instant timestamp
) {
    public static ApiErrorResponse of(ErrorCode code, String message, Map<String, Object> details, String traceId) {
        return new ApiErrorResponse(code.name(), message, details == null ? Map.of() : details, traceId, Instant.now());
    }
}
