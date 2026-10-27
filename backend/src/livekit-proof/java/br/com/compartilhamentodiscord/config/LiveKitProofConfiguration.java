package br.com.compartilhamentodiscord.config;

import br.com.compartilhamentodiscord.integration.livekit.LiveKitGateway;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitProperties;
import br.com.compartilhamentodiscord.security.LiveKitProofAccessFilter;
import br.com.compartilhamentodiscord.security.SecuritySupport;
import br.com.compartilhamentodiscord.service.LiveKitProofService;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

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
    @Order(10)
    SecurityFilterChain liveKitProofSecurity(HttpSecurity http, Environment environment) throws Exception {
        String origin = environment.getRequiredProperty("livekit.proof.origin");
        if (!"http://127.0.0.1:4200".equals(origin)) {
            throw new IllegalStateException("Origem da prova local inválida.");
        }
        var paths = PathPatternRequestMatcher.withDefaults();
        var routes = new OrRequestMatcher(
                paths.matcher(org.springframework.http.HttpMethod.POST, "/api/dev/livekit/rooms"),
                paths.matcher(org.springframework.http.HttpMethod.DELETE, "/api/dev/livekit/rooms"),
                paths.matcher(org.springframework.http.HttpMethod.GET, "/api/dev/livekit/rooms/{name}"),
                paths.matcher(org.springframework.http.HttpMethod.DELETE, "/api/dev/livekit/rooms/{name}"),
                paths.matcher(org.springframework.http.HttpMethod.POST, "/api/dev/livekit/rooms/{name}/tokens"));
        SecuritySupport.configure(http);
        // Only these cookie-free, custom-header + exact-Origin local capabilities are exempt.
        // CORS is closed; a foreign browser origin cannot supply the required marker.
        http.securityMatcher(routes).csrf(csrf -> csrf.ignoringRequestMatchers(routes))
                .addFilterBefore(new LiveKitProofAccessFilter(origin), AuthorizationFilter.class)
                .authorizeHttpRequests(access -> access.anyRequest().permitAll());
        return http.build();
    }

    private static boolean isLoopback(URI uri) {
        return uri != null && "127.0.0.1".equals(uri.getHost()) && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null;
    }
}
