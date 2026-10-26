package br.com.compartilhamentodiscord.controller;

import br.com.compartilhamentodiscord.dto.response.CaptureAccessResponse;
import br.com.compartilhamentodiscord.dto.response.CaptureStatusResponse;
import br.com.compartilhamentodiscord.service.CaptureProofService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/capture/session")
@Profile("capture-proof & !prod & !production & !livekit-proof")
@ConditionalOnProperty(name = "capture.proof.enabled", havingValue = "true")
public class CaptureProofController {
    private final CaptureProofService service;
    public CaptureProofController(CaptureProofService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CaptureAccessResponse create(@RequestHeader("X-Capture-Key") String key) { return service.create(key); }
    @PostMapping("/viewer")
    public CaptureAccessResponse viewer(@RequestHeader("X-Capture-Key") String key) { return service.viewer(key); }
    @GetMapping
    public CaptureStatusResponse status(@RequestHeader("X-Capture-Key") String key) { return service.status(key); }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Capture-Key") String key) { service.delete(key); }
}
