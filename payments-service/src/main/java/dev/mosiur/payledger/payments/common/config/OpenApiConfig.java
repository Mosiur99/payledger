package dev.mosiur.payledger.payments.common.config;

import dev.mosiur.payledger.payments.common.web.Idempotent;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI payLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayLedger API")
                        .version("v1")
                        .description("""
                                Accounts, deposits, transfers and statements on a double-entry ledger.
                                Amounts are whole numbers in the currency's smallest unit (50000 = 500.00).
                                Every call needs a Keycloak access token. Customers have the USER role; deposits need ADMIN.
                                Errors use RFC 9457 Problem Details with a stable `type` such as `/problems/insufficient-funds`."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    OperationCustomizer documentIdempotencyKey() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(Idempotent.class)) {
                operation.addParametersItem(new HeaderParameter()
                        .name(IdempotencyFilter.KEY_HEADER)
                        .required(true)
                        .description("Unique per request, for example a UUID. Retrying with the same key never repeats the operation.")
                        .schema(new StringSchema().maxLength(64)));
            }
            return operation;
        };
    }
}
