package br.com.compartilhamentodiscord.integration.livekit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Checks the actual SDK-generated credential, rather than a mocked grant builder. */
class LiveKitTokenTest {
    private static final String TEST_KEY = "unit-test-key";
    private static final String TEST_SECRET = "unit-test-only-secret-with-32-characters";

    @Test
    void presenterCredentialIsSignedScopedShortLivedAndLimitedToScreenSources() throws Exception {
        MediaAccessToken credential = gateway().issueToken("test-room", "test-presenter", MediaRole.PRESENTER);
        Map<String, Object> claims = verifiedClaims(credential.token());
        assertEquals("test-presenter", claims.get("sub"));
        assertEquals(TEST_KEY, claims.get("iss"));
        assertEquals(120L, number(claims, "exp") - number(claims, "nbf"));
        assertEquals(120L, credential.expiresInSeconds());
        Map<?, ?> grants = (Map<?, ?>) claims.get("video");
        assertEquals(Map.of("room", "test-room", "roomJoin", true, "canPublish", true,
                "canSubscribe", false, "canPublishData", false, "canUpdateOwnMetadata", false,
                "canPublishSources", List.of("screen_share", "screen_share_audio")), grants);
        assertFalse(credential.toString().contains(credential.token()));
    }

    @Test
    void viewerCredentialCannotPublishMediaDataOrAdministerRooms() throws Exception {
        Map<String, Object> claims = verifiedClaims(gateway().issueToken("test-room", "test-viewer", MediaRole.VIEWER).token());
        assertEquals("test-viewer", claims.get("sub"));
        assertEquals(Map.of("room", "test-room", "roomJoin", true, "canPublish", false,
                "canSubscribe", true, "canPublishData", false, "canUpdateOwnMetadata", false), claims.get("video"));
        assertEquals(120L, number(claims, "exp") - number(claims, "nbf"));
    }

    @Test
    void disabledConfigurationNeedsNoCredentialsButEnabledConfigurationDoes() {
        LiveKitProperties properties = new LiveKitProperties();
        assertFalse(properties.isEnabled());
        properties.setEnabled(true);
        IllegalStateException failure = assertThrows(IllegalStateException.class, properties::validateEnabledConfiguration);
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(TEST_SECRET));
    }

    private static LiveKitGateway gateway() {
        LiveKitProperties properties = new LiveKitProperties();
        properties.setEnabled(true);
        properties.setApiKey(TEST_KEY);
        properties.setApiSecret(TEST_SECRET);
        properties.validateEnabledConfiguration();
        return new LiveKitGateway(null, properties);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> verifiedClaims(String token) throws Exception {
        String[] segments = token.split("\\.");
        assertEquals(3, segments.length);
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = hmac.doFinal((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(segments[2])), "Assinatura JWT deve ser válida.");
        return new ObjectMapper().readValue(Base64.getUrlDecoder().decode(segments[1]), Map.class);
    }

    private static long number(Map<String, Object> claims, String name) {
        return ((Number) claims.get(name)).longValue();
    }
}
