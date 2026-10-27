package br.com.compartilhamentodiscord.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Fail closed until T-07 can resolve a real, scoped, in-memory access context. */
@Component
public final class ProductAccessPolicy implements AuthorizationManager<RequestAuthorizationContext> {
    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
                                            RequestAuthorizationContext context) {
        return new AuthorizationDecision(false);
    }
}
