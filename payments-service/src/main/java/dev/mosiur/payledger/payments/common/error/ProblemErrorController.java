package dev.mosiur.payledger.payments.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's /error page. The servlet container sends errors that escape the filter chain here,
 * outside Spring MVC's exception handling, so without this they would not be Problem Details.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    static final String INTERNAL_DETAIL = "Something went wrong on our side. Please try again later.";

    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = statusOf(request);
        ProblemDetail problem = status.is5xxServerError()
                ? ProblemType.INTERNAL.problem(INTERNAL_DETAIL)
                : ProblemDetail.forStatus(status);
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path != null) {
            problem.setInstance(URI.create(path.toString()));
        }
        return ResponseEntity.status(status).body(problem);
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
