package br.com.compartilhamentodiscord.service.access;

/** T-08/T-09 must supply verified binding, membership and live session evidence. */
public interface AccessVerifier {
    enum Result { ALLOWED, DENIED, UNAVAILABLE }
    Result verify(AccessStore.Grant grant);
}
