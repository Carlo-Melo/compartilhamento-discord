package br.com.compartilhamentodiscord.integration.livekit;

public record LiveKitRoom(String name, String sid, int maxParticipants, int participantCount) { }
