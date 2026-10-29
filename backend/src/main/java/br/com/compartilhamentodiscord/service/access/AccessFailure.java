package br.com.compartilhamentodiscord.service.access;

/** Fixed error categories only; never retain request data or credentials. */
public final class AccessFailure extends RuntimeException {
    public enum Kind { INVALID, DENIED, UNAVAILABLE, REQUIRED }
    private final Kind kind;
    public AccessFailure(Kind kind) { this.kind = kind; }
    public Kind kind() { return kind; }
}
