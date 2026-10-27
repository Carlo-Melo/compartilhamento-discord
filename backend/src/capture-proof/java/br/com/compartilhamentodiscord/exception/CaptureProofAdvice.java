package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.controller.CaptureProofController;
import br.com.compartilhamentodiscord.dto.response.ApiError;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitIntegrationException;
import br.com.compartilhamentodiscord.service.CaptureProofException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CaptureProofController.class)
@Profile("capture-proof & !prod & !production & !livekit-proof")
@ConditionalOnProperty(name = "capture.proof.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CaptureProofAdvice {
    @ExceptionHandler(CaptureProofException.class)
    ResponseEntity<ApiError> request(CaptureProofException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(ApiErrors.body(request, exception.code(),
                exception.getMessage(), exception.status().value() == 503));
    }
    @ExceptionHandler(LiveKitIntegrationException.class)
    ResponseEntity<ApiError> integration(HttpServletRequest request) {
        return ResponseEntity.status(503).body(ApiErrors.body(request, "SFU_UNAVAILABLE",
                "SFU indisponível. Confira a configuração local e tente novamente.", true));
    }
}
