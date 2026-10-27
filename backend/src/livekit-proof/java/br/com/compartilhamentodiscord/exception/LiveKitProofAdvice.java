package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.controller.LiveKitProofController;
import br.com.compartilhamentodiscord.dto.response.ApiError;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitIntegrationException;
import br.com.compartilhamentodiscord.service.LiveKitProofException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LiveKitProofController.class)
@Profile("livekit-proof & !prod & !production")
@ConditionalOnProperty(name = "livekit.proof.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LiveKitProofAdvice {
    @ExceptionHandler(LiveKitIntegrationException.class)
    ResponseEntity<ApiError> integration(HttpServletRequest request) {
        return ResponseEntity.status(503).body(ApiErrors.body(request, "SFU_UNAVAILABLE",
                "SFU local indisponível ou configuração inválida. Verifique os serviços locais.", true));
    }

    @ExceptionHandler(LiveKitProofException.class)
    ResponseEntity<ApiError> request(LiveKitProofException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(ApiErrors.body(request, "LOCAL_PROOF_REJECTED",
                exception.getMessage(), exception.status().value() == 503));
    }
}
