package com.example.reservation.api.error;

import com.example.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, String message, HttpStatus status) {
        this(code, message, status, Map.of());
    }

    public ApiException(ErrorCode code, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }
}
