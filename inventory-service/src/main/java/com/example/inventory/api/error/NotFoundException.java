package com.example.inventory.api.error;

import com.example.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NotFoundException extends ApiException {
    public NotFoundException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, HttpStatus.NOT_FOUND, details);
    }
}
