package br.com.compartilhamentodiscord.dto.response;

/** Public error contract. Never carries rejected input or provider details. */
public record ApiError(String code, String message, String requestId, boolean retryable) { }
