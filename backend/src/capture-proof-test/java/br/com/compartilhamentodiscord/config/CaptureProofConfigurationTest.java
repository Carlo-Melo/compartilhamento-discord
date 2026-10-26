package br.com.compartilhamentodiscord.config;

import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.integration.livekit.LiveKitProperties;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

class CaptureProofConfigurationTest {
    @Test
    void separateConfigurationRequiresLoopbackBackendAndAdminApiWithSameOriginWss() throws Exception {
        ServerProperties server = new ServerProperties();
        server.setAddress(InetAddress.getByName("127.0.0.1"));
        LiveKitProperties properties = new LiveKitProperties();
        properties.setEnabled(true);
        properties.setBrowserUrl(URI.create("wss://192.168.50.10:4443"));
        assertDoesNotThrow(() -> CaptureProofConfiguration.validate(server, properties, "https://192.168.50.10:4443"));
        assertThrows(IllegalStateException.class, () -> CaptureProofConfiguration.validate(server, properties, "http://192.168.50.10:4443"));
        assertThrows(IllegalStateException.class, () -> CaptureProofConfiguration.validate(server, properties, "https://192.168.50.10:4443/path"));
        properties.setBrowserUrl(URI.create("wss://other.invalid:4443"));
        assertThrows(IllegalStateException.class, () -> CaptureProofConfiguration.validate(server, properties, "https://192.168.50.10:4443"));
        properties.setBrowserUrl(URI.create("wss://192.168.50.10:4443"));
        properties.setApiUrl(URI.create("http://192.168.50.10:7880"));
        assertThrows(IllegalStateException.class, () -> CaptureProofConfiguration.validate(server, properties, "https://192.168.50.10:4443"));
        properties.setApiUrl(URI.create("http://127.0.0.1:7880"));
        server.setAddress(InetAddress.getByName("0.0.0.0"));
        assertThrows(IllegalStateException.class, () -> CaptureProofConfiguration.validate(server, properties, "https://192.168.50.10:4443"));
    }
}
