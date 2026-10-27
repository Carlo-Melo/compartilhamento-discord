package br.com.compartilhamentodiscord.dto.response;

import br.com.compartilhamentodiscord.integration.livekit.MediaRole;

/** Confirms only the capability's role; never allocates a room or media token. */
public record CaptureValidationResponse(MediaRole role) { }
