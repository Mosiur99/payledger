package dev.mosiur.payledger.payments.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemErrorControllerTest {

    private final ProblemErrorController controller = new ProblemErrorController();

    @Test
    void aServerErrorBecomesTheInternalProblemWithoutDetails() {
        ResponseEntity<ProblemDetail> response = controller.error(errorRequest(500, "/api/v1/transfers"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ProblemDetail problem = response.getBody();
        assertThat(problem.getType()).isEqualTo(URI.create("/problems/internal"));
        assertThat(problem.getTitle()).isEqualTo("Internal error");
        assertThat(problem.getDetail()).isEqualTo(ProblemErrorController.INTERNAL_DETAIL);
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/transfers"));
    }

    @Test
    void anyOtherStatusKeepsItsStatusAndStandardTitle() {
        ResponseEntity<ProblemDetail> response = controller.error(errorRequest(404, "/nowhere"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getTitle()).isEqualTo("Not Found");
        assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/nowhere"));
    }

    @Test
    void aMissingStatusIsTreatedAsAServerError() {
        ResponseEntity<ProblemDetail> response = controller.error(new MockHttpServletRequest("GET", "/error"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    private static MockHttpServletRequest errorRequest(int status, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        return request;
    }
}
