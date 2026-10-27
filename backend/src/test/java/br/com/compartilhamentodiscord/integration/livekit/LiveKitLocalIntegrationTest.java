package br.com.compartilhamentodiscord.integration.livekit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import livekit.LivekitModels;
import livekit.LivekitRtc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicitly opt-in; uses a real local SFU and deletes only its own UUID-named rooms. */
@EnabledIfEnvironmentVariable(named = "LIVEKIT_REAL_TEST_ENABLED", matches = "true")
class LiveKitLocalIntegrationTest {
    @Test
    void realSfuAuthenticationFailureDoesNotExposeCredentialsOrProviderBody() {
        LiveKitProperties properties = localProperties();
        properties.setApiKey("t02-nonexistent-key-" + UUID.randomUUID());
        LiveKitGateway gateway = new LiveKitConfiguration().liveKitGateway(properties);
        LiveKitIntegrationException failure = assertThrows(LiveKitIntegrationException.class,
                () -> gateway.createRoom("t02-junit-" + UUID.randomUUID()));
        assertEquals(LiveKitIntegrationException.Reason.AUTHENTICATION_FAILED, failure.getReason());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(properties.getApiKey()));
        assertFalse(failure.getMessage().contains(properties.getApiSecret()));
        assertEquals("Não foi possível concluir a operação na SFU local.", failure.getMessage());
    }

    @Test
    void realSfuScopesConnectionsAndCannotRecreateDeletedRoomWithUnexpiredToken() throws Exception {
        LiveKitProperties properties = localProperties();
        LiveKitGateway gateway = new LiveKitConfiguration().liveKitGateway(properties);
        String roomA = "t02-junit-" + UUID.randomUUID();
        String roomB = "t02-junit-" + UUID.randomUUID();
        SignalListener listener = new SignalListener();
        WebSocket socket = null;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertEquals(10, gateway.createRoom(roomA).maxParticipants());
            assertEquals(10, gateway.createRoom(roomB).maxParticipants());
            assertEquals(roomA, gateway.findRoom(roomA).orElseThrow().name());
            assertEquals(roomB, gateway.findRoom(roomB).orElseThrow().name());

            MediaAccessToken tokenA = gateway.issueToken(roomA, "t02-java-scope", MediaRole.VIEWER);
            long issuedAtNanos = System.nanoTime();
            URI attemptOtherRoom = URI.create(properties.getBrowserUrl() +
                    "/rtc?room=" + roomB + "&protocol=15&sdk=js&version=t02-proof&auto_subscribe=false");
            // The desired foreign room is adversarial input; credentials stay in the header.
            socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + tokenA.token())
                    .buildAsync(attemptOtherRoom, listener).get(8, TimeUnit.SECONDS);
            LivekitRtc.JoinResponse join = listener.join.get(8, TimeUnit.SECONDS);
            assertEquals(roomA, join.getRoom().getName(), "O grant assinado deve prevalecer sobre a query da sala B.");
            assertEquals("t02-java-scope", join.getParticipant().getIdentity());
            assertEquals(0, gateway.listParticipants(roomB).size(), "Token da sala A não deve entrar na B.");
            assertTrue(gateway.listParticipants(roomA).stream().anyMatch(p -> "t02-java-scope".equals(p.identity())));

            // This credential comes from the SFU signal, never a second gateway issuance.
            String refreshed = listener.refreshed.get(8, TimeUnit.SECONDS);
            assertFalse(tokenA.token().equals(refreshed), "A SFU deve entregar uma credencial renovada real.");
            assertRefreshedScope(refreshed, properties, roomA, "t02-java-scope");

            // Bypass all browser SDK guards and ask the SFU to publish directly.
            assertPublicationDenied(socket, listener, LivekitModels.TrackType.VIDEO, LivekitModels.TrackSource.SCREEN_SHARE);
            assertPublicationDenied(socket, listener, LivekitModels.TrackType.AUDIO, LivekitModels.TrackSource.SCREEN_SHARE_AUDIO);
            assertTrue(gateway.listParticipants(roomA).stream().allMatch(p -> p.tracks().isEmpty()));

            gateway.deleteRoom(roomA);
            listener.disconnected.get(8, TimeUnit.SECONDS);
            listener.closed.get(8, TimeUnit.SECONDS);
            assertTrue(gateway.findRoom(roomA).isEmpty());
            assertTrue(Duration.ofNanos(System.nanoTime() - issuedAtNanos).getSeconds() < tokenA.expiresInSeconds(),
                    "A negação por sala ausente deve ser medida antes do vencimento do token.");
            int reconnectStatus;
            try {
                WebSocket unexpected = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                        .header("Authorization", "Bearer " + tokenA.token())
                        .buildAsync(attemptOtherRoom, new SignalListener()).get(8, TimeUnit.SECONDS);
                unexpected.abort();
                reconnectStatus = 101;
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof WebSocketHandshakeException handshake) {
                    reconnectStatus = handshake.getResponse().statusCode();
                } else {
                    throw new AssertionError("Falha inesperada de transporte no reuso do token; consulte a SFU local.");
                }
            }
            assertEquals(404, reconnectStatus, "Sala apagada deve continuar ausente mesmo com token ainda válido.");
            assertTrue(gateway.findRoom(roomA).isEmpty());
            assertEquals(0, gateway.listParticipants(roomB).size());
            assertJoinDenied(client, attemptOtherRoom, refreshed);
            // B is a distinct still-live generation. The retired generation's initial and refreshed
            // credentials cannot select it, even when a visitor explicitly requests its room name.
            assertTrue(gateway.findRoom(roomB).isPresent());
            assertJoinDenied(client, attemptOtherRoom, tokenA.token());
            assertEquals(0, gateway.listParticipants(roomB).size());
        } finally {
            try {
                if (socket != null) { socket.abort(); }
            } finally {
                // assertAll attempts both cleanups even if one room operation fails.
                assertAll("Limpeza das salas criadas exclusivamente por este teste",
                        () -> {
                            gateway.deleteRoom(roomA);
                            assertTrue(gateway.findRoom(roomA).isEmpty());
                        },
                        () -> {
                            gateway.deleteRoom(roomB);
                            assertTrue(gateway.findRoom(roomB).isEmpty());
                        });
            }
        }
    }

    private static void assertJoinDenied(HttpClient client, URI uri, String token) throws Exception {
        try {
            WebSocket unexpected = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token)
                    .buildAsync(uri, new SignalListener()).get(8, TimeUnit.SECONDS);
            unexpected.abort();
            fail("JWT da geração excluída não pode recriar sala ou entrar em geração diferente.");
        } catch (ExecutionException failure) {
            if (!(failure.getCause() instanceof WebSocketHandshakeException handshake)) {
                throw new AssertionError("Falha de transporte não comprova negação pela SFU.");
            }
            assertEquals(404, handshake.getResponse().statusCode());
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertRefreshedScope(String token, LiveKitProperties properties,
            String room, String identity) throws Exception {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(properties.getApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertTrue(MessageDigest.isEqual(hmac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII)),
                Base64.getUrlDecoder().decode(parts[2])), "Assinatura do token renovado deve ser válida.");
        Map<String, Object> claims = new ObjectMapper().readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
        // Boolean-only assertions avoid ever putting credential claims into a failure report.
        assertTrue(identity.equals(claims.get("sub")), "Token renovado deve manter identidade.");
        Map<?, ?> grant = (Map<?, ?>) claims.get("video");
        assertTrue(room.equals(grant.get("room")), "Token renovado deve manter geração.");
        assertTrue(Boolean.FALSE.equals(grant.get("canPublish")), "Renovação não pode liberar publicação.");
        assertTrue(Boolean.TRUE.equals(grant.get("canSubscribe")), "Renovação mantém apenas assinatura.");
        assertFalse(Boolean.TRUE.equals(grant.get("roomCreate")));
        assertFalse(Boolean.TRUE.equals(grant.get("roomAdmin")));
        assertTrue(((Number) claims.get("exp")).longValue() > java.time.Instant.now().getEpochSecond());
    }

    private static void assertPublicationDenied(WebSocket socket, SignalListener listener,
            LivekitModels.TrackType kind, LivekitModels.TrackSource source) throws Exception {
        LivekitRtc.SignalRequest request = LivekitRtc.SignalRequest.newBuilder().setAddTrack(
                LivekitRtc.AddTrackRequest.newBuilder().setCid("t02-attempt-" + source.name())
                        .setName("mídia sintética negada").setType(kind).setSource(source)).build();
        socket.sendBinary(ByteBuffer.wrap(request.toByteArray()), true).get(5, TimeUnit.SECONDS);
        LivekitRtc.RequestResponse response = listener.requests.poll(5, TimeUnit.SECONDS);
        assertNotNull(response, "A SFU deve responder explicitamente à tentativa de publicação.");
        assertEquals(LivekitRtc.RequestResponse.Reason.NOT_ALLOWED, response.getReason());
    }

    private static LiveKitProperties localProperties() {
        assertEquals("http://127.0.0.1:7880", System.getenv("LIVEKIT_INTERNAL_URL"),
                "Teste opt-in requer exatamente a SFU local documentada.");
        assertEquals("ws://127.0.0.1:7880", System.getenv("LIVEKIT_PUBLIC_URL"));
        LiveKitProperties properties = new LiveKitProperties();
        properties.setEnabled(true);
        properties.setApiUrl(URI.create(System.getenv("LIVEKIT_INTERNAL_URL")));
        properties.setBrowserUrl(URI.create(System.getenv("LIVEKIT_PUBLIC_URL")));
        properties.setApiKey(System.getenv("LIVEKIT_API_KEY"));
        properties.setApiSecret(System.getenv("LIVEKIT_API_SECRET"));
        properties.validateEnabledConfiguration();
        return properties;
    }

    private static final class SignalListener implements WebSocket.Listener {
        private final CompletableFuture<LivekitRtc.JoinResponse> join = new CompletableFuture<>();
        private final CompletableFuture<Void> disconnected = new CompletableFuture<>();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private final CompletableFuture<String> refreshed = new CompletableFuture<>();
        private final BlockingQueue<LivekitRtc.RequestResponse> requests = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();

        @Override
        public void onOpen(WebSocket webSocket) { webSocket.request(1); }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            fragments.writeBytes(bytes);
            if (last) {
                try {
                    LivekitRtc.SignalResponse response = LivekitRtc.SignalResponse.parseFrom(fragments.toByteArray());
                    if (response.hasJoin()) { join.complete(response.getJoin()); }
                    if (response.hasLeave()) { disconnected.complete(null); }
                    if (response.hasRequestResponse()) { requests.add(response.getRequestResponse()); }
                    if (!response.getRefreshToken().isEmpty()) { refreshed.complete(response.getRefreshToken()); }
                } catch (Exception ignored) {
                    join.completeExceptionally(new IllegalStateException("Resposta de sinalização inválida da SFU local."));
                }
                fragments.reset();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            disconnected.complete(null);
            closed.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            join.completeExceptionally(new IllegalStateException("Sinalização da SFU local indisponível."));
            disconnected.completeExceptionally(new IllegalStateException("Sinalização da SFU local indisponível."));
        }
    }
}
