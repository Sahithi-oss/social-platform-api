package com.socialplatform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a Redis guardrail cap is exceeded.
 * Maps to HTTP 429 Too Many Requests.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class GuardrailException extends RuntimeException {

    public GuardrailException(String message) {
        super(message);
    }
}
