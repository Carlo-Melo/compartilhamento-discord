package br.com.compartilhamentodiscord.controller;

import br.com.compartilhamentodiscord.dto.response.HealthResponse;
import br.com.compartilhamentodiscord.service.HealthService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health/live")
    public ResponseEntity<HealthResponse> liveness() {
        HealthResponse health = healthService.liveness();
        HttpStatus status = "UP".equals(health.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(health);
    }
}
