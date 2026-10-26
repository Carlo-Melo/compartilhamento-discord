package br.com.compartilhamentodiscord.service;

import br.com.compartilhamentodiscord.dto.response.HealthResponse;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final ApplicationAvailability availability;

    public HealthService(ApplicationAvailability availability) {
        this.availability = availability;
    }

    public HealthResponse liveness() {
        return new HealthResponse(availability.getLivenessState() == LivenessState.CORRECT ? "UP" : "DOWN");
    }
}
