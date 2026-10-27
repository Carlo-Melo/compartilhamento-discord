package br.com.compartilhamentodiscord.dto.response;

public record CaptureStatusResponse(boolean exists, boolean live, String presenterIdentity,
                                    String roomName, int viewerCount, int viewerSlotsUsed, int maxViewers) { }
