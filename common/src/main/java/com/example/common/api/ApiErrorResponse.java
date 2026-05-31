package com.example.common.api;

import java.time.Instant;
import java.util.Map;

/**
 * Shared error response shape returned by both services.
 */
public record ApiErrorResponse(

        String code,

        String message,

        Map<String, Object> details,

        String traceId,

        Instant timestamp
) {

    /**
     * Creates a response with a non-null details map so clients can parse errors consistently.
     */
    public static ApiErrorResponse of(ErrorCode code, String message, Map<String, Object> details, String traceId) {
        return new ApiErrorResponse(code.name(), message, details == null ? Map.of() : details, traceId, Instant.now());
    }
}
