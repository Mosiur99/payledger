package dev.mosiur.payledger.payments.idempotency.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.common.web.CallerHeader;
import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyDecision;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import dev.mosiur.payledger.payments.idempotency.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    private static final String BODY = "{\"amountMinor\":100}";

    @Mock
    private IdempotencyService idempotencyService;

    private IdempotencyFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(idempotencyService, JsonMapper.builder().build());
        response = new MockHttpServletResponse();
    }

    @Test
    void runsANewRequestAndStoresItsResponse() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), eq("k1"), anyString())).thenReturn(new IdempotencyDecision.Proceed());
        AtomicReference<String> bodySeenByController = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            bodySeenByController.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            res.setContentType("application/json");
            ((HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":\"t1\"}");
        };

        filter.doFilter(request("k1"), response, chain);

        assertThat(bodySeenByController.get()).isEqualTo(BODY);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"t1\"}");
        ArgumentCaptor<StoredResponse> stored = ArgumentCaptor.forClass(StoredResponse.class);
        verify(idempotencyService).complete(eq("ayesha"), eq("k1"), stored.capture());
        assertThat(stored.getValue().status()).isEqualTo(201);
        assertThat(stored.getValue().body()).isEqualTo("{\"id\":\"t1\"}");
    }

    @Test
    void replaysAStoredResponseWithoutRunningTheRequest() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), eq("k1"), anyString())).thenReturn(
                new IdempotencyDecision.Replay(new StoredResponse(201, "application/json", "{\"id\":\"t1\"}")));

        filter.doFilter(request("k1"), response, (req, res) -> {
            throw new AssertionError("must not run");
        });

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"t1\"}");
    }

    @Test
    void rejectsAReusedKey() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), eq("k1"), anyString())).thenReturn(new IdempotencyDecision.KeyReused());

        filter.doFilter(request("k1"), response, (req, res) -> {
            throw new AssertionError("must not run");
        });

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("Idempotency key reused");
    }

    @Test
    void rejectsAKeyThatIsStillInProgress() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), eq("k1"), anyString())).thenReturn(new IdempotencyDecision.InProgress());

        filter.doFilter(request("k1"), response, (req, res) -> {
            throw new AssertionError("must not run");
        });

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void requiresAKey() throws Exception {
        filter.doFilter(request(null), response, (req, res) -> {
            throw new AssertionError("must not run");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void releasesTheKeyAfterAServerError() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), eq("k1"), anyString())).thenReturn(new IdempotencyDecision.Proceed());

        filter.doFilter(request("k1"), response,
                (req, res) -> ((HttpServletResponse) res).setStatus(503));

        verify(idempotencyService).release("ayesha", "k1");
        verify(idempotencyService, never()).complete(anyString(), anyString(), any());
    }

    @Test
    void releasesTheKeyWhenTheRequestThrows() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), eq("k1"), anyString())).thenReturn(new IdempotencyDecision.Proceed());

        assertThatThrownBy(() -> filter.doFilter(request("k1"), response, (req, res) -> {
            throw new IllegalStateException("database down");
        })).isInstanceOf(IllegalStateException.class);

        verify(idempotencyService).release("ayesha", "k1");
    }

    @Test
    void sameBodyGivesTheSameHashAndADifferentBodyDoesNot() throws Exception {
        when(idempotencyService.begin(eq("ayesha"), anyString(), anyString())).thenReturn(new IdempotencyDecision.InProgress());

        filter.doFilter(request("a"), new MockHttpServletResponse(), (req, res) -> { });
        filter.doFilter(request("b"), new MockHttpServletResponse(), (req, res) -> { });
        MockHttpServletRequest other = request("c");
        other.setContent("{\"amountMinor\":101}".getBytes(StandardCharsets.UTF_8));
        filter.doFilter(other, new MockHttpServletResponse(), (req, res) -> { });

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService, times(3)).begin(eq("ayesha"), anyString(), hashes.capture());
        assertThat(hashes.getAllValues().get(0)).hasSize(64).isEqualTo(hashes.getAllValues().get(1));
        assertThat(hashes.getAllValues().get(2)).isNotEqualTo(hashes.getAllValues().get(0));
    }

    @Test
    void ignoresRequestsThatDoNotMoveMoney() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/accounts");
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        filter.doFilter(get, response, (req, res) -> ran.set(true));

        assertThat(ran.get()).isTrue();
        verifyNoInteractions(idempotencyService);
    }

    private static MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers");
        request.addHeader(CallerHeader.NAME, "ayesha");
        if (key != null) {
            request.addHeader(IdempotencyFilter.KEY_HEADER, key);
        }
        request.setContentType("application/json");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
