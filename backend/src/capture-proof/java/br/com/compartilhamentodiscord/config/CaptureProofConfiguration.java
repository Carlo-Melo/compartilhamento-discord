package br.com.compartilhamentodiscord.config;

import br.com.compartilhamentodiscord.integration.livekit.CaptureProofMedia;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitGateway;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitProperties;
import br.com.compartilhamentodiscord.security.CaptureProofAccessFilter;
import br.com.compartilhamentodiscord.security.CaptureProofAccessPolicy;
import br.com.compartilhamentodiscord.service.CaptureProofService;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("capture-proof & !prod & !production & !livekit-proof")
@ConditionalOnProperty(name = "capture.proof.enabled", havingValue = "true")
public class CaptureProofConfiguration {
    @Bean
    CaptureProofAccessPolicy captureProofPolicy(Environment environment) {
        return new CaptureProofAccessPolicy(environment.getRequiredProperty("capture.proof.presenter-key"),
                environment.getRequiredProperty("capture.proof.viewer-key"));
    }

    @Bean
    CaptureProofService captureProofService(LiveKitGateway gateway, LiveKitProperties properties,
                                           ServerProperties server, CaptureProofAccessPolicy policy,
                                           Environment environment) {
        validate(server, properties, environment.getRequiredProperty("capture.proof.origin"));
        return new CaptureProofService(CaptureProofMedia.liveKit(gateway), policy, properties.getBrowserUrl());
    }

    @Bean
    FilterRegistrationBean<CaptureProofAccessFilter> captureProofFilter(Environment environment,
                                                                       CaptureProofAccessPolicy policy) {
        var registration = new FilterRegistrationBean<>(new CaptureProofAccessFilter(
                environment.getRequiredProperty("capture.proof.origin"), policy));
        registration.addUrlPatterns("/api/dev/capture/*");
        registration.setOrder(-100);
        return registration;
    }

    public static void validate(ServerProperties server, LiveKitProperties properties, String value) {
        try {
            URI origin = URI.create(value);
            URI browser = properties.getBrowserUrl();
            URI api = properties.getApiUrl();
            boolean originValid = "https".equals(origin.getScheme()) && privateIpv4(origin.getHost())
                    && origin.getPort() > 0 && origin.getRawUserInfo() == null && origin.getRawQuery() == null
                    && origin.getRawFragment() == null && (origin.getRawPath() == null || origin.getRawPath().isEmpty());
            boolean browserValid = browser != null && "wss".equals(browser.getScheme())
                    && origin.getRawAuthority().equals(browser.getRawAuthority()) && browser.getRawUserInfo() == null
                    && browser.getRawQuery() == null && browser.getRawFragment() == null
                    && (browser.getRawPath() == null || browser.getRawPath().isEmpty());
            boolean apiValid = api != null && "http".equals(api.getScheme()) && "127.0.0.1".equals(api.getHost())
                    && api.getRawUserInfo() == null && api.getRawQuery() == null && api.getRawFragment() == null
                    && (api.getRawPath() == null || api.getRawPath().isEmpty());
            if (server.getAddress() == null || !server.getAddress().isLoopbackAddress() || !properties.isEnabled()
                    || !originValid || !browserValid || !apiValid) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("T-03 exige backend/API SFU em loopback e origem HTTPS exata com sinalização WSS na mesma origem.");
        }
    }

    private static boolean privateIpv4(String host) {
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = host.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255 || !Integer.toString(octets[i]).equals(parts[i])) return false;
        }
        return octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }
}
