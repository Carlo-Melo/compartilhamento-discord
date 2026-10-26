package br.com.compartilhamentodiscord.service;

import org.springframework.http.HttpStatus;

public final class CaptureProofException extends RuntimeException {
    private final HttpStatus status;
    public CaptureProofException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
