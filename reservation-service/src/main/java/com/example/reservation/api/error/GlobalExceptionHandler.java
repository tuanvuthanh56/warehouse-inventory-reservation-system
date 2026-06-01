package com.example.reservation.api.error;

import com.example.common.api.ApiResponse;
import com.example.common.api.ApiErrorResponse;
import com.example.common.api.ErrorCode;
import com.example.reservation.domain.exception.ReservationDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts reservation-side exceptions into the shared API error response format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status())
                .body(ApiResponse.failure(ApiErrorResponse.of(ex.code(), ex.getMessage(), ex.details(), traceId(request))));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed.",
                details,
                traceId(request)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(response));
    }

    @ExceptionHandler(ReservationDomainException.class)
    ResponseEntity<ApiResponse<Void>> handleDomainException(ReservationDomainException ex, HttpServletRequest request) {
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                ex.getMessage(),
                Map.of(),
                traceId(request)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(response));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                "Unexpected server error.",
                Map.of(),
                traceId(request)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(response));
    }

    private String traceId(HttpServletRequest request) {
        String header = request.getHeader("X-Trace-Id");
        return header == null || header.isBlank() ? request.getRequestId() : header;
    }
}
