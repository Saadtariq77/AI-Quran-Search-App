package com.example.aiquran.exception;

/** Permanent — bad request, bad key, blocked content. Retrying won't help. */
public class GeminiRequestException extends RuntimeException {
    public GeminiRequestException(String message, Throwable cause) {
        super(message, cause);
    }
    public GeminiRequestException(String message) {
        super(message);
    }
}