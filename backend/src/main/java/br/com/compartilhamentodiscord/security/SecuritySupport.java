package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.exception.ApiErrors;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.context.NullSecurityContextRepository;

/** Shared transport protection; experimental chains must still enforce their own capabilities. */
public final class SecuritySupport {
    private SecuritySupport() { }

    public static void configure(HttpSecurity http) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> ApiErrors.write(request, response,
                                401, "ACCESS_REQUIRED", "Obtenha um novo acesso privado pelo bot no Discord.", false))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrf = exception instanceof CsrfException;
                            ApiErrors.write(request, response, 403, csrf ? "CSRF_INVALID" : "FORBIDDEN",
                                    csrf ? "Proteção CSRF inválida. Atualize a página e tente novamente."
                                            : "Você não tem permissão para esta operação.", false);
                        }))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .addHeaderWriter((request, response) -> {
                            response.setHeader("Permissions-Policy", "camera=(), microphone=(), display-capture=(self)");
                            response.setHeader("Cache-Control", "no-store");
                        }));
    }
}
