package dev.mosiur.payledger.payments.common.config;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class ApiDocsIT {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void publishesTheOpenApiDocument() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("PayLedger API")
                .jsonPath("$.paths['/api/v1/accounts'].post").exists()
                .jsonPath("$.paths['/api/v1/accounts/{accountId}/statement'].get").exists()
                .jsonPath("$.paths['/api/v1/transfers'].post.parameters[?(@.name == 'Idempotency-Key')].required")
                .isEqualTo(true)
                .jsonPath("$.paths['/api/v1/deposits'].post.parameters[?(@.name == 'Idempotency-Key')]").exists()
                .jsonPath("$.paths['/api/v1/accounts'].get.parameters[?(@.name == 'Idempotency-Key')]").doesNotExist();
    }

    @Test
    void servesSwaggerUi() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }
}
