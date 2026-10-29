package br.com.compartilhamentodiscord.config;

import br.com.compartilhamentodiscord.security.BrowserOriginFilter;
import br.com.compartilhamentodiscord.security.ProductAccessPolicy;
import br.com.compartilhamentodiscord.security.SecuritySupport;
import br.com.compartilhamentodiscord.security.UnavailableProofFilter;
import br.com.compartilhamentodiscord.security.ExchangeRateFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    ServletContextInitializer bootstrapCookieSecurity(Environment environment) {
        boolean secure = environment.getRequiredProperty("app.security.origin").startsWith("https://");
        return context -> context.getSessionCookieConfig().setSecure(secure);
    }

    @Bean
    @Order(0)
    SecurityFilterChain managementSecurity(HttpSecurity http, Environment environment) throws Exception {
        SecuritySupport.configure(http);
        http.securityMatcher(request -> request.getLocalPort() == environment.getProperty(
                        "local.management.port", Integer.class, environment.getProperty("management.server.port", Integer.class, 8081)))
                .authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(request -> "GET".equals(request.getMethod())
                                && "/health/ready".equals(request.getServletPath())
                                && request.getLocalAddr() != null
                                && ("127.0.0.1".equals(request.getLocalAddr()) || "0:0:0:0:0:0:0:1".equals(request.getLocalAddr())
                                    || "::1".equals(request.getLocalAddr())))
                        .permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(100)
    SecurityFilterChain productSecurity(HttpSecurity http, ProductAccessPolicy policy, Environment environment) throws Exception {
        SecuritySupport.configure(http);
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-XSRF-TOKEN");
        // A synchronizer token is bound to the bootstrap HttpSession. The readable cookie alone cannot authorize a mutation.
        http.csrf(csrf -> csrf.csrfTokenRepository(repository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler() {
                            @Override
                            public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
                                return request.getHeader(token.getHeaderName());
                            }
                        }))
                .addFilterBefore(new UnavailableProofFilter(), CsrfFilter.class)
                .addFilterBefore(new BrowserOriginFilter(environment.getRequiredProperty("app.security.origin")), CsrfFilter.class)
                .addFilterAfter(new ExchangeRateFilter(), CsrfFilter.class)
                .authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/health/live", "/api/csrf").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/access/exchange").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/access/session").permitAll()
                        .anyRequest().access(policy));
        return http.build();
    }
}
