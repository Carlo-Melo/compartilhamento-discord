package br.com.compartilhamentodiscord.dto.response;

public record CsrfResponse(String headerName, String parameterName, String token) { }
