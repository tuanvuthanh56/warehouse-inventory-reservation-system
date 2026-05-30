package com.example.inventory.api.error;

import com.example.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ConflictException extends ApiException {
    public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, HttpStatus.CONFLICT, details);
    }
}
