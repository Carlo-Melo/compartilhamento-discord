package br.com.compartilhamentodiscord.dto.response;

import java.util.List;

public record ProofRoomResponse(String roomName, boolean exists, int maxParticipants,
                                List<Participant> participants) {
    public record Participant(String identity, String sid, List<Track> tracks) {}
    public record Track(String sid, String kind, String source, boolean muted) {}
}
