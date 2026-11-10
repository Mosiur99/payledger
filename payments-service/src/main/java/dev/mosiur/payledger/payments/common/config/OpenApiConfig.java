package dev.mosiur.payledger.payments.common.config;

import dev.mosiur.payledger.payments.common.web.CallerHeader;
import dev.mosiur.payledger.payments.common.web.Idempotent;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI payLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PayLedger API")
                .version("v1")
                .description("""
                        Accounts, deposits, transfers and statements on a double-entry ledger.
                        Amounts are whole numbers in the currency's smallest unit (50000 = 500.00).
                        Errors use RFC 9457 Problem Details with a stable `type` such as `/problems/insufficient-funds`."""));
    }

    @Bean
    OperationCustomizer documentHeaders() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().stream()
                        .filter(parameter -> CallerHeader.NAME.equals(parameter.getName()))
                        .forEach(parameter -> parameter.description(
                                "Caller identity. Temporary until JWT authentication is added."));
            }
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
