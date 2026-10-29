package br.com.compartilhamentodiscord.service.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
    private static final String CONTEXT = AccessService.class.getName() + ".context";
    private final AccessStore store;
    private final AccessVerifier verifier;

    public AccessService(AccessStore store, AccessVerifier verifier) {
        this.store = store;
        this.verifier = verifier;
    }

    public AccessStore.Context exchange(String ticket, HttpServletRequest request) {
        var consumed = store.consume(ticket).orElseThrow(() -> new AccessFailure(AccessFailure.Kind.INVALID));
        AccessVerifier.Result result;
        try { result = verifier.verify(consumed.grant()); }
        catch (RuntimeException failure) { throw new AccessFailure(AccessFailure.Kind.UNAVAILABLE); }
        if (result == AccessVerifier.Result.DENIED) throw new AccessFailure(AccessFailure.Kind.DENIED);
        if (result != AccessVerifier.Result.ALLOWED) throw new AccessFailure(AccessFailure.Kind.UNAVAILABLE);
        HttpSession session = request.getSession(false);
        if (session == null) throw new AccessFailure(AccessFailure.Kind.UNAVAILABLE);
        synchronized (session) {
            String previous = (String) session.getAttribute(CONTEXT);
            AccessStore.Context context;
            try {
                context = store.activate(consumed, previous)
                        .orElseThrow(() -> new AccessFailure(AccessFailure.Kind.INVALID));
            } catch (AccessStore.LimitException failure) {
                throw new AccessFailure(AccessFailure.Kind.UNAVAILABLE);
            }
            try {
                request.changeSessionId();
                session.setAttribute(CONTEXT, context.id());
                return context;
            } catch (RuntimeException failure) {
                store.revoke(context.id());
                throw new AccessFailure(AccessFailure.Kind.UNAVAILABLE);
            }
        }
    }

    public AccessStore.Context current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new AccessFailure(AccessFailure.Kind.REQUIRED);
        Object id = session.getAttribute(CONTEXT);
        if (!(id instanceof String contextId)) throw new AccessFailure(AccessFailure.Kind.REQUIRED);
        return store.resolve(contextId).orElseThrow(() -> new AccessFailure(AccessFailure.Kind.REQUIRED));
    }
}
