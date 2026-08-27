package com.example.aiquran.exception;

/** Transient — worth retrying later. 429/500/502/503/504/timeouts. */
public class GeminiUnavailableException extends RuntimeException {
    public GeminiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
    public GeminiUnavailableException(String message) {
        super(message);
    }
}