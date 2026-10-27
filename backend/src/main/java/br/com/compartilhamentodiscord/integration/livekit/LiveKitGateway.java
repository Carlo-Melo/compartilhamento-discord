package br.com.compartilhamentodiscord.integration.livekit;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanUpdateOwnMetadata;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import retrofit2.Call;
import retrofit2.Response;

/** Administrative SDK calls stay on the backend. This class never transports media. */
public final class LiveKitGateway {
    public static final int MAX_PARTICIPANTS = 10;
    public static final long TOKEN_TTL_SECONDS = 120;
    private final RoomServiceClient rooms;
    private final LiveKitProperties properties;

    LiveKitGateway(RoomServiceClient rooms, LiveKitProperties properties) {
        this.rooms = rooms;
        this.properties = properties;
    }

    public LiveKitRoom createRoom(String roomName) {
        requireIdentifier(roomName);
        return mapRoom(requireBody(execute(rooms.createRoom(roomName, 300, MAX_PARTICIPANTS))));
    }

    public Optional<LiveKitRoom> findRoom(String roomName) {
        requireIdentifier(roomName);
        return requireBody(execute(rooms.listRooms(List.of(roomName)))).stream()
                .filter(room -> roomName.equals(room.getName())).findFirst().map(LiveKitGateway::mapRoom);
    }

    public List<LiveKitParticipant> listParticipants(String roomName) {
        requireIdentifier(roomName);
        return requireBody(execute(rooms.listParticipants(roomName))).stream().map(participant ->
                new LiveKitParticipant(participant.getIdentity(), participant.getSid(),
                        participant.getTracksList().stream().map(track -> new LiveKitTrack(track.getSid(),
                                track.getType().name(), track.getSource().name(), track.getMuted())).toList()))
                .toList();
    }

    public void deleteRoom(String roomName) {
        requireIdentifier(roomName);
        // DeleteRoom is idempotent for rooms already absent from the SFU.
        Response<Void> response = executeRaw(rooms.deleteRoom(roomName));
        if (!response.isSuccessful() && response.code() != 404) {
            reject(response);
        }
        closeErrorBody(response);
    }

    /** Disconnects the current participant; self-hosted JWTs are not individually revoked. */
    public void removeParticipant(String roomName, String identity) {
        requireIdentifier(roomName);
        requireIdentifier(identity);
        Response<Void> response = executeRaw(rooms.removeParticipant(roomName, identity));
        if (!response.isSuccessful() && response.code() != 404) reject(response);
        closeErrorBody(response);
    }

    public MediaAccessToken issueToken(String roomName, String identity, MediaRole role) {
        requireIdentifier(roomName);
        requireIdentifier(identity);
        Objects.requireNonNull(role, "Papel de mídia obrigatório.");
        try {
            AccessToken access = new AccessToken(properties.getApiKey(), properties.getApiSecret());
            access.setIdentity(identity);
            Instant issuedAt = Instant.now();
            access.setNotBefore(Date.from(issuedAt));
            access.setExpiration(Date.from(issuedAt.plusSeconds(TOKEN_TTL_SECONDS)));
            access.addGrants(new RoomJoin(true), new RoomName(roomName),
                    new CanPublish(role == MediaRole.PRESENTER), new CanSubscribe(role == MediaRole.VIEWER),
                    new CanPublishData(false), new CanUpdateOwnMetadata(false));
            if (role == MediaRole.PRESENTER) {
                access.addGrants(new CanPublishSources(List.of("screen_share", "screen_share_audio")));
            }
            return new MediaAccessToken(access.toJwt(), TOKEN_TTL_SECONDS);
        } catch (RuntimeException exception) {
            throw new LiveKitIntegrationException(LiveKitIntegrationException.Reason.UNAVAILABLE);
        }
    }

    private static LiveKitRoom mapRoom(LivekitModels.Room room) {
        return new LiveKitRoom(room.getName(), room.getSid(), room.getMaxParticipants(), room.getNumParticipants());
    }

    private static void requireIdentifier(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9:_-]{1,200}")) {
            throw new IllegalArgumentException("Identificador de mídia inválido.");
        }
    }

    private static <T> Response<T> execute(Call<T> call) {
        Response<T> response = executeRaw(call);
        if (!response.isSuccessful()) { reject(response); }
        return response;
    }

    private static <T> Response<T> executeRaw(Call<T> call) {
        try {
            return call.execute();
        } catch (IOException | RuntimeException exception) {
            throw new LiveKitIntegrationException(LiveKitIntegrationException.Reason.UNAVAILABLE);
        }
    }

    private static <T> T requireBody(Response<T> response) {
        if (response.body() == null) {
            throw new LiveKitIntegrationException(LiveKitIntegrationException.Reason.UNAVAILABLE);
        }
        return response.body();
    }

    private static void reject(Response<?> response) {
        int status = response.code();
        closeErrorBody(response);
        LiveKitIntegrationException.Reason reason = status == 401 || status == 403
                ? LiveKitIntegrationException.Reason.AUTHENTICATION_FAILED
                : status >= 500 || status == 408 || status == 429
                    ? LiveKitIntegrationException.Reason.UNAVAILABLE : LiveKitIntegrationException.Reason.REJECTED;
        throw new LiveKitIntegrationException(reason);
    }

    private static void closeErrorBody(Response<?> response) {
        if (response.errorBody() != null) { response.errorBody().close(); }
    }
}
