package br.com.compartilhamentodiscord.controller;

import br.com.compartilhamentodiscord.dto.response.ProofRoomResponse;
import br.com.compartilhamentodiscord.dto.response.ProofTokenResponse;
import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import br.com.compartilhamentodiscord.service.LiveKitProofService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/livekit/rooms")
@Profile("livekit-proof & !prod & !production")
@ConditionalOnProperty(name = "livekit.proof.enabled", havingValue = "true")
public class LiveKitProofController {
    private final LiveKitProofService service;

    public LiveKitProofController(LiveKitProofService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProofRoomResponse create() { return service.create(); }

    @GetMapping("/{name}")
    public ProofRoomResponse inspect(@PathVariable String name) { return service.inspect(name); }

    @PostMapping("/{name}/tokens")
    public ProofTokenResponse token(@PathVariable String name, @Valid @RequestBody TokenRequest request) {
        return service.token(name, request.role());
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) { service.delete(name); }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cleanup() { service.cleanup(); }

    public record TokenRequest(@NotNull MediaRole role) {}
}
