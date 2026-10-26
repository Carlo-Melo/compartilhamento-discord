package br.com.compartilhamentodiscord.integration.livekit;

import java.util.List;
import java.util.Optional;

/** Small test seam for administrative operations; media never traverses Java. */
public interface CaptureProofMedia {
    void create(String room);
    Optional<LiveKitRoom> find(String room);
    List<LiveKitParticipant> participants(String room);
    MediaAccessToken token(String room, String identity, MediaRole role);
    void delete(String room);

    static CaptureProofMedia liveKit(LiveKitGateway gateway) {
        return new CaptureProofMedia() {
            public void create(String room) { gateway.createRoom(room); }
            public Optional<LiveKitRoom> find(String room) { return gateway.findRoom(room); }
            public List<LiveKitParticipant> participants(String room) { return gateway.listParticipants(room); }
            public MediaAccessToken token(String room, String identity, MediaRole role) {
                return gateway.issueToken(room, identity, role);
            }
            public void delete(String room) { gateway.deleteRoom(room); }
        };
    }
}
