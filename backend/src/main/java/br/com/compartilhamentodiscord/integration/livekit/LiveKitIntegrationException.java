package br.com.compartilhamentodiscord.integration.livekit;

/** Deliberately excludes provider bodies, URLs, credentials and nested SDK exceptions. */
public final class LiveKitIntegrationException extends RuntimeException {
    public enum Reason { UNAVAILABLE, AUTHENTICATION_FAILED, REJECTED }
    private final Reason reason;

    LiveKitIntegrationException(Reason reason) {
        super("Não foi possível concluir a operação na SFU local.");
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
