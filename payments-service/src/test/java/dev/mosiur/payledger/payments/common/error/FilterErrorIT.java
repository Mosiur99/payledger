package dev.mosiur.payledger.payments.common.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.idempotency.service.IdempotencyService;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import dev.mosiur.payledger.payments.support.TestJwtConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Errors thrown by a filter skip Spring MVC's exception handling and reach the container's /error page. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, TestJwtConfiguration.class})
class FilterErrorIT {

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private IdempotencyService idempotencyService;

    @Test
    void anExceptionInAFilterIsStillAProblemDetailsResponse() {
        doThrow(new IllegalStateException("connection refused: idempotency-db:5432"))
                .when(idempotencyService).begin(any(), any(), any());

        RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .post().uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user("ayesha-" + UUID.randomUUID()))
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"fromAccountId\":\"" + UUID.randomUUID() + "\",\"toAccountId\":\"" + UUID.randomUUID()
                        + "\",\"amountMinor\":100}")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("/problems/internal")
                .jsonPath("$.title").isEqualTo("Internal error")
                .jsonPath("$.instance").isEqualTo("/api/v1/transfers")
                .jsonPath("$.detail").isEqualTo(ProblemErrorController.INTERNAL_DETAIL);
    }
}
