package br.com.compartilhamentodiscord.service;

import org.springframework.http.HttpStatus;

public final class CaptureProofException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public CaptureProofException(HttpStatus status, String message) { this(status, "CAPTURE_PROOF_REJECTED", message); }
    public CaptureProofException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
