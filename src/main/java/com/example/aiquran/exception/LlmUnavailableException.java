package com.example.aiquran.exception;

/**
 * Indicates transient LLM provider failure (e.g. HTTP 429 rate limit, 5xx outage, network timeout).
 */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmUnavailableException(String message) {
        super(message);
    }
}
