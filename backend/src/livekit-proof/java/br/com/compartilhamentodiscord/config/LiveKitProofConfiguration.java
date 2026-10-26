package br.com.compartilhamentodiscord.config;

import br.com.compartilhamentodiscord.integration.livekit.LiveKitGateway;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitProperties;
import br.com.compartilhamentodiscord.security.LiveKitProofAccessFilter;
import br.com.compartilhamentodiscord.service.LiveKitProofService;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("livekit-proof & !prod & !production")
@ConditionalOnProperty(name = "livekit.proof.enabled", havingValue = "true")
public class LiveKitProofConfiguration {

    @Bean
    LiveKitProofService liveKitProofService(LiveKitGateway gateway, LiveKitProperties properties,
                                           ServerProperties server) {
        if (server.getAddress() == null || !server.getAddress().isLoopbackAddress()
                || !isLoopback(properties.getApiUrl()) || !isLoopback(properties.getBrowserUrl())) {
            throw new IllegalStateException("A prova LiveKit exige backend e SFU restritos ao loopback.");
        }
        return new LiveKitProofService(gateway, properties);
    }

    @Bean
    FilterRegistrationBean<LiveKitProofAccessFilter> liveKitProofAccess(Environment environment) {
        String origin = environment.getRequiredProperty("livekit.proof.origin");
        if (!"http://127.0.0.1:4200".equals(origin)) {
            throw new IllegalStateException("Origem da prova local inválida.");
        }
        var filter = new FilterRegistrationBean<>(new LiveKitProofAccessFilter(origin));
        filter.addUrlPatterns("/api/dev/livekit/*");
        filter.setOrder(-100);
        return filter;
    }

    private static boolean isLoopback(URI uri) {
        return uri != null && "127.0.0.1".equals(uri.getHost()) && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null;
    }
}
