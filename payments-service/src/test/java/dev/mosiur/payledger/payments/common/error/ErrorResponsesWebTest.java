package dev.mosiur.payledger.payments.common.error;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.domain.InsufficientFundsException;
import dev.mosiur.payledger.payments.account.service.AccountFrozenException;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import dev.mosiur.payledger.payments.common.security.ProblemSecurityHandler;
import dev.mosiur.payledger.payments.common.security.SecurityConfig;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import dev.mosiur.payledger.payments.ledger.controller.StatementController;
import dev.mosiur.payledger.payments.ledger.service.CurrencyMismatchException;
import dev.mosiur.payledger.payments.ledger.service.StatementService;
import dev.mosiur.payledger.payments.transfer.controller.TransferController;
import dev.mosiur.payledger.payments.transfer.service.SameAccountTransferException;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(
        controllers = {TransferController.class, StatementController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = IdempotencyFilter.class))
@Import({SecurityConfig.class, ProblemSecurityHandler.class, ProblemResponseWriter.class})
class ErrorResponsesWebTest {

    private static final UUID FROM = UUID.fromString("0199a000-0000-7000-8000-000000000001");
    private static final UUID TO = UUID.fromString("0199a000-0000-7000-8000-000000000002");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private StatementService statementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void insufficientFundsExplainsTheShortfall() throws Exception {
        when(transferService.transfer(anyString(), any())).thenThrow(new InsufficientFundsException(FROM, 12_000, 50_000));

        transfer(validBody())
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/insufficient-funds"))
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Account " + FROM + " has 12000 available; 50000 requested."))
                .andExpect(jsonPath("$.instance").value("/api/v1/transfers"))
                .andExpect(jsonPath("$.accountId").value(FROM.toString()))
                .andExpect(jsonPath("$.availableMinor").value(12_000))
                .andExpect(jsonPath("$.requestedMinor").value(50_000));
    }

    @Test
    void currencyMismatch() throws Exception {
        when(transferService.transfer(anyString(), any())).thenThrow(new CurrencyMismatchException(Currency.BDT, Currency.EUR));

        transfer(validBody())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("/problems/currency-mismatch"))
                .andExpect(jsonPath("$.detail").value("Cannot move money from a BDT account to a EUR account."));
    }

    @Test
    void sameAccountTransfer() throws Exception {
        when(transferService.transfer(anyString(), any())).thenThrow(new SameAccountTransferException(FROM));

        transfer(validBody())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("/problems/same-account-transfer"));
    }

    @Test
    void accountNotFound() throws Exception {
        when(transferService.transfer(anyString(), any())).thenThrow(new AccountNotFoundException(TO));

        transfer(validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/account-not-found"))
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void accountFrozen() throws Exception {
        when(transferService.transfer(anyString(), any())).thenThrow(new AccountFrozenException(FROM));

        transfer(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/account-frozen"));
    }

    @Test
    void invalidFieldsAreListed() throws Exception {
        transfer("{\"fromAccountId\":\"" + FROM + "\",\"amountMinor\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation"))
                .andExpect(jsonPath("$.errors[?(@.field == 'toAccountId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'amountMinor')]").exists());
    }

    @Test
    void malformedJsonIsAValidationProblem() throws Exception {
        transfer("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation"))
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void missingTokenIsUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/unauthenticated"))
                .andExpect(jsonPath("$.instance").value("/api/v1/transfers"));
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + FROM + "/statement")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/forbidden"));
    }

    @Test
    void invalidQueryParameterIsListed() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + FROM + "/statement?limit=500").with(ayesha()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void malformedIdInThePathIsAValidationProblem() throws Exception {
        mvc.perform(get("/api/v1/accounts/not-a-uuid/statement").with(ayesha()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation"));
    }

    @Test
    void unexpectedErrorsHideInternalDetails() throws Exception {
        when(statementService.statement(anyString(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("password=secret in connection string"));

        mvc.perform(get("/api/v1/accounts/" + FROM + "/statement").with(ayesha()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("/problems/internal"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    private ResultActions transfer(String body) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .with(ayesha())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static JwtRequestPostProcessor ayesha() {
        return jwt().jwt(token -> token.subject("ayesha")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static String validBody() {
        return "{\"fromAccountId\":\"" + FROM + "\",\"toAccountId\":\"" + TO + "\",\"amountMinor\":50000}";
    }
}
