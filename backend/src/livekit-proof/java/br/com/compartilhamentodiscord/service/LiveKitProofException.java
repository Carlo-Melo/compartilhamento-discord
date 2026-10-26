package br.com.compartilhamentodiscord.service;

import org.springframework.http.HttpStatus;

public final class LiveKitProofException extends RuntimeException {
    private final HttpStatus status;

    public LiveKitProofException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
