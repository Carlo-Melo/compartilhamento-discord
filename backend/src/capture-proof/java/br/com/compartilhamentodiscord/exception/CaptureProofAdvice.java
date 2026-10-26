package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.controller.CaptureProofController;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitIntegrationException;
import br.com.compartilhamentodiscord.service.CaptureProofException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CaptureProofController.class)
@Profile("capture-proof & !prod & !production & !livekit-proof")
@ConditionalOnProperty(name = "capture.proof.enabled", havingValue = "true")
public class CaptureProofAdvice {
    @ExceptionHandler(CaptureProofException.class)
    ResponseEntity<Failure> request(CaptureProofException exception) {
        return ResponseEntity.status(exception.status()).body(new Failure("CAPTURE_PROOF_REJECTED", exception.getMessage()));
    }
    @ExceptionHandler(LiveKitIntegrationException.class)
    ResponseEntity<Failure> integration() {
        return ResponseEntity.status(503).body(new Failure("SFU_UNAVAILABLE", "SFU indisponível. Confira a configuração local e tente novamente."));
    }
    public record Failure(String code, String message) { }
}
