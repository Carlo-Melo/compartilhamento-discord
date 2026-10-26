package br.com.compartilhamentodiscord.integration.livekit;

import java.util.List;

public record LiveKitParticipant(String identity, String sid, List<LiveKitTrack> tracks) {
    public LiveKitParticipant {
        tracks = List.copyOf(tracks);
    }
}
