package br.com.compartilhamentodiscord.security;

import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import br.com.compartilhamentodiscord.service.CaptureProofException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureProofAccessTest {
    private static final String PRESENTER = "test-presenter-capability-with-32-characters";
    private static final String VIEWER = "test-viewer-capability-with-32-characters";
    private static final String ORIGIN = "https://192.0.2.1:4443";

    @Test
    void accessKeysMustBeDistinctAndStrongAndNeverAppearInFailure() {
        assertThrows(IllegalStateException.class, () -> new CaptureProofAccessPolicy(PRESENTER, PRESENTER));
        assertThrows(IllegalStateException.class, () -> new CaptureProofAccessPolicy("short", VIEWER));
        var policy = new CaptureProofAccessPolicy(PRESENTER, VIEWER);
        assertEquals(MediaRole.PRESENTER, policy.authenticate(PRESENTER));
        assertEquals(MediaRole.VIEWER, policy.authenticate(VIEWER));
        var failure = assertThrows(CaptureProofException.class, () -> policy.authenticate("invalid"));
        assertFalse(failure.getMessage().contains(PRESENTER));
        assertThrows(CaptureProofException.class, () -> policy.authenticate(null));
    }

    @Test
    void filterRequiresLoopbackExactHostOriginMarkerAndRoleKey() throws Exception {
        var filter = new CaptureProofAccessFilter(ORIGIN, new CaptureProofAccessPolicy(PRESENTER, VIEWER));
        assertTrue(run(filter, "POST", "127.0.0.1", headers()).passed);
        assertTrue(run(filter, "GET", "127.0.0.1", without("Origin")).passed);
        assertFalse(run(filter, "POST", "192.0.2.2", headers()).passed);
        assertFalse(run(filter, "POST", "127.0.0.1", without("Origin")).passed);
        assertFalse(run(filter, "POST", "127.0.0.1", changed("Origin", "https://outside.invalid")).passed);
        assertFalse(run(filter, "POST", "127.0.0.1", changed("Host", "127.0.0.1:8080")).passed);
        assertFalse(run(filter, "POST", "127.0.0.1", without("X-Capture-Proof")).passed);
        Result rejected = run(filter, "POST", "127.0.0.1", changed("X-Capture-Key", "invalid"));
        assertFalse(rejected.passed);
        assertEquals(403, rejected.status);
        assertFalse(rejected.body.contains(PRESENTER));
        assertEquals("no-store", rejected.responseHeaders.get("Cache-Control"));
    }

    private static Map<String, String> headers() {
        return new HashMap<>(Map.of("Host", "192.0.2.1:4443", "Origin", ORIGIN,
                "X-Capture-Proof", "restricted-development", "X-Capture-Key", PRESENTER));
    }
    private static Map<String, String> without(String key) { var headers = headers(); headers.remove(key); return headers; }
    private static Map<String, String> changed(String key, String value) { var headers = headers(); headers.put(key, value); return headers; }

    private static Result run(CaptureProofAccessFilter filter, String method, String remote, Map<String, String> headers) throws Exception {
        var result = new Result();
        var writer = new StringWriter();
        var attributes = new HashMap<String, Object>();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(CaptureProofAccessTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, operation, args) -> switch (operation.getName()) {
                    case "getRemoteAddr" -> remote;
                    case "getMethod" -> method;
                    case "getServletPath" -> "/api/dev/capture/session";
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getAttribute" -> attributes.get((String) args[0]);
                    case "setAttribute" -> attributes.put((String) args[0], args[1]);
                    default -> throw new UnsupportedOperationException(operation.getName());
                });
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(CaptureProofAccessTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, operation, args) -> {
                    switch (operation.getName()) {
                        case "setHeader" -> result.responseHeaders.put((String) args[0], (String) args[1]);
                        case "setStatus" -> result.status = (int) args[0];
                        case "setContentType", "setCharacterEncoding" -> { }
                        case "getWriter" -> { return new PrintWriter(writer); }
                        case "getStatus" -> { return result.status == 0 ? 200 : result.status; }
                        default -> throw new UnsupportedOperationException(operation.getName());
                    }
                    return null;
                });
        filter.doFilterInternal(request, response, (req, res) -> result.passed = true);
        result.body = writer.toString();
        return result;
    }
    private static final class Result {
        boolean passed;
        int status;
        String body;
        Map<String, String> responseHeaders = new HashMap<>();
    }
}
