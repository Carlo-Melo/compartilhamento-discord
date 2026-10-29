package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.dto.response.ApiError;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitIntegrationException;
import br.com.compartilhamentodiscord.service.access.AccessFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Does not echo exception messages, binding errors, paths or provider bodies. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {
    @ExceptionHandler(AccessFailure.class)
    ResponseEntity<ApiError> accessFailure(AccessFailure exception, HttpServletRequest request) {
        return switch (exception.kind()) {
            case INVALID -> response(request, 401, "ACCESS_INVALID", "Acesso inválido ou expirado. Obtenha novo acesso pelo bot.", false);
            case REQUIRED -> response(request, 401, "ACCESS_REQUIRED", "Obtenha um novo acesso pelo bot no Discord.", false);
            case DENIED -> response(request, 403, "ACCESS_DENIED", "Acesso não autorizado. Retorne ao bot no Discord.", false);
            case UNAVAILABLE -> response(request, 503, "SERVICE_UNAVAILABLE", "Não foi possível verificar o acesso. Obtenha novo acesso pelo bot.", true);
        };
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception exception, HttpServletRequest request) {
        return response(request, 400, "INVALID_REQUEST", "Entrada inválida. Confira os dados enviados.", false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return response(request, 403, "FORBIDDEN", "Você não tem permissão para esta operação.", false);
    }

    @ExceptionHandler(LiveKitIntegrationException.class)
    ResponseEntity<ApiError> integration(HttpServletRequest request) {
        return response(request, 503, "MEDIA_UNAVAILABLE", "Serviço de mídia indisponível. Tente novamente mais tarde.", true);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse error) return forStatus(request, error.getStatusCode().value());
        return forStatus(request, 500);
    }

    static ResponseEntity<ApiError> forStatus(HttpServletRequest request, int status) {
        return switch (status) {
            case 400 -> response(request, status, "INVALID_REQUEST", "Entrada inválida. Confira os dados enviados.", false);
            case 401 -> response(request, status, "ACCESS_REQUIRED", "Obtenha um novo acesso pelo bot no Discord.", false);
            case 403 -> response(request, status, "FORBIDDEN", "Você não tem permissão para esta operação.", false);
            case 404 -> response(request, status, "NOT_FOUND", "Recurso não encontrado.", false);
            case 405 -> response(request, status, "METHOD_NOT_ALLOWED", "Método não permitido para este recurso.", false);
            case 415 -> response(request, status, "UNSUPPORTED_MEDIA_TYPE", "Formato de conteúdo não aceito.", false);
            case 503 -> response(request, status, "SERVICE_UNAVAILABLE", "Serviço indisponível. Tente novamente mais tarde.", true);
            default -> status >= 400 && status < 500
                    ? response(request, status, "INVALID_REQUEST", "Não foi possível atender à solicitação.", false)
                    : response(request, 500, "INTERNAL_ERROR", "Não foi possível concluir a operação. Tente novamente mais tarde.", false);
        };
    }

    private static ResponseEntity<ApiError> response(HttpServletRequest request, int status, String code,
                                                      String message, boolean retryable) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Request-Id", br.com.compartilhamentodiscord.security.RequestIdFilter.requestId(request))
                .body(ApiErrors.body(request, code, message, retryable));
    }
}
