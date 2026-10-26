package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.controller.LiveKitProofController;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitIntegrationException;
import br.com.compartilhamentodiscord.service.LiveKitProofException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LiveKitProofController.class)
@Profile("livekit-proof & !prod & !production")
@ConditionalOnProperty(name = "livekit.proof.enabled", havingValue = "true")
public class LiveKitProofAdvice {
    @ExceptionHandler(LiveKitIntegrationException.class)
    ResponseEntity<Failure> integration(LiveKitIntegrationException exception) {
        return ResponseEntity.status(503).body(new Failure("SFU_UNAVAILABLE",
                "SFU local indisponível ou configuração inválida. Verifique os serviços locais."));
    }

    @ExceptionHandler(LiveKitProofException.class)
    ResponseEntity<Failure> request(LiveKitProofException exception) {
        return ResponseEntity.status(exception.status()).body(new Failure("LOCAL_PROOF_REJECTED", exception.getMessage()));
    }

    public record Failure(String code, String message) {}
}
