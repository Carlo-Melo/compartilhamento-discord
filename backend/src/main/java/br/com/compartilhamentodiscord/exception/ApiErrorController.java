package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.dto.response.ApiError;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sanitized fallback for servlet error dispatches; no HTML or internal error attributes. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return ApiExceptionHandler.forStatus(request, status instanceof Integer value ? value : 500);
    }
}
