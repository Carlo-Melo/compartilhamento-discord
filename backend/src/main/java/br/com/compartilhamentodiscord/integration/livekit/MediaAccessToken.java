package br.com.compartilhamentodiscord.integration.livekit;

/** A short-lived credential kept only in memory. Never log its token accessor. */
public record MediaAccessToken(String token, long expiresInSeconds) {
    @Override
    public String toString() {
        return "MediaAccessToken[token=REDACTED, expiresInSeconds=" + expiresInSeconds + "]";
    }
}
