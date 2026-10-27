package br.com.compartilhamentodiscord.controller;

import br.com.compartilhamentodiscord.dto.response.CsrfResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    private final boolean secureOrigin;

    public CsrfController(@Value("${app.security.origin}") String origin) {
        this.secureOrigin = origin.startsWith("https://");
    }

    @GetMapping("/api/csrf")
    public CsrfResponse csrf(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        var cookie = ResponseCookie.from("XSRF-TOKEN", token.getToken()).path("/")
                .secure(secureOrigin || request.isSecure()).httpOnly(false).sameSite("Lax").build();
        // Append rather than replacing Set-Cookie: generating the token also creates the bootstrap session cookie.
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }
}
