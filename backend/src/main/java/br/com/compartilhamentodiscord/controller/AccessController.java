package br.com.compartilhamentodiscord.controller;

import br.com.compartilhamentodiscord.dto.response.AccessContextResponse;
import br.com.compartilhamentodiscord.dto.response.AccessExchangeResponse;
import br.com.compartilhamentodiscord.service.access.AccessService;
import br.com.compartilhamentodiscord.service.access.AccessStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
public class AccessController {
    private final AccessService access;
    public AccessController(AccessService access) { this.access = access; }

    @PostMapping("/api/access/exchange")
    public AccessExchangeResponse exchange(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!body.isObject() || body.size() != 1 || !body.has("ticket") || !body.get("ticket").isTextual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var context = access.exchange(body.get("ticket").asText(), request);
        String next = "/s/" + context.grant().sessionId()
                + (context.grant().role() == AccessStore.Role.OWNER ? "/transmitir" : "");
        return new AccessExchangeResponse(AccessContextResponse.from(context), next);
    }

    @GetMapping("/api/access/session")
    public AccessContextResponse session(HttpServletRequest request) {
        return AccessContextResponse.from(access.current(request));
    }
}
