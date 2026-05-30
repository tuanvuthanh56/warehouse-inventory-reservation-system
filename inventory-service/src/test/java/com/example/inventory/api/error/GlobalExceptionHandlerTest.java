package com.example.inventory.api.error;

import com.example.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiExceptionUsesStatusCodeDetailsAndTraceHeader() {
        var request = request("trace-1", "generated");
        var exception = new NotFoundException(ErrorCode.SKU_NOT_FOUND, "Missing SKU.", Map.of("sku", "A100"));

        var response = handler.handleApiException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SKU_NOT_FOUND");
        assertThat(response.getBody().details()).containsEntry("sku", "A100");
        assertThat(response.getBody().traceId()).isEqualTo("trace-1");
    }

    @Test
    void handleValidationCollectsFieldErrorsAndFallsBackToRequestId() {
        var request = request("", "req-1");
        var exception = mock(MethodArgumentNotValidException.class);
        var bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("request", "sku", "must not be blank")));

        var response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().details()).containsEntry("sku", "must not be blank");
        assertThat(response.getBody().traceId()).isEqualTo("req-1");
    }

    @Test
    void handleUnexpectedReturnsInternalError() {
        var response = handler.handleUnexpected(new RuntimeException("boom"), request(null, "req-2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().details()).isEmpty();
        assertThat(response.getBody().traceId()).isEqualTo("req-2");
    }

    private HttpServletRequest request(String traceId, String requestId) {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Trace-Id")).thenReturn(traceId);
        when(request.getRequestId()).thenReturn(requestId);
        return request;
    }
}
