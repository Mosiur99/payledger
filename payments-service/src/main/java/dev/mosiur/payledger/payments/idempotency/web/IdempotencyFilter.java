package dev.mosiur.payledger.payments.idempotency.web;

import dev.mosiur.payledger.payments.common.error.ProblemResponseWriter;
import dev.mosiur.payledger.payments.common.error.ProblemType;
import dev.mosiur.payledger.payments.idempotency.domain.IdempotencyDecision;
import dev.mosiur.payledger.payments.idempotency.domain.StoredResponse;
import dev.mosiur.payledger.payments.idempotency.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes money-moving POST requests safe to retry: the first request with a key runs,
 * repeats of the same request get the stored response, and anything else is refused.
 * Runs after Spring Security, so the caller is already authenticated and keys are scoped per caller.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Set<String> PROTECTED_PATHS = Set.of("/api/v1/transfers", "/api/v1/deposits");
    private static final int MAX_LENGTH = 64;

    private final IdempotencyService idempotencyService;
    private final ProblemResponseWriter problemWriter;

    public IdempotencyFilter(IdempotencyService idempotencyService, ProblemResponseWriter problemWriter) {
        this.idempotencyService = idempotencyService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        if (caller == null || !caller.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        String owner = caller.getName();
        String key = request.getHeader(KEY_HEADER);
        if (!isValid(key)) {
            problemWriter.write(request, response, ProblemType.IDEMPOTENCY_KEY_REQUIRED,
                    "Send a unique " + KEY_HEADER + " header of 1 to " + MAX_LENGTH + " characters.");
            return;
        }

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String requestHash = hash(cachedRequest);

        switch (idempotencyService.begin(owner, key, requestHash)) {
            case IdempotencyDecision.Proceed() -> execute(cachedRequest, response, chain, owner, key);
            case IdempotencyDecision.Replay(StoredResponse stored) -> replay(response, stored);
            case IdempotencyDecision.InProgress() -> problemWriter.write(request, response,
                    ProblemType.IDEMPOTENCY_IN_PROGRESS, "A request with this idempotency key is still being processed.");
            case IdempotencyDecision.KeyReused() -> problemWriter.write(request, response,
                    ProblemType.IDEMPOTENCY_KEY_REUSED, "This idempotency key was already used for a different request.");
        }
    }

    private void execute(CachedBodyRequest request, HttpServletResponse response, FilterChain chain,
            String owner, String key) throws ServletException, IOException {
        ContentCachingResponseWrapper recorder = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, recorder);
        } catch (ServletException | IOException | RuntimeException e) {
            idempotencyService.release(owner, key);
            throw e;
        }

        if (recorder.getStatus() >= 500) {
            idempotencyService.release(owner, key);
        } else {
            idempotencyService.complete(owner, key, new StoredResponse(
                    recorder.getStatus(),
                    recorder.getContentType(),
                    new String(recorder.getContentAsByteArray(), StandardCharsets.UTF_8)));
        }
        recorder.copyBodyToResponse();
    }

    private static void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        response.setHeader(REPLAYED_HEADER, "true");
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        if (stored.body() != null) {
            response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean isValid(String header) {
        return header != null && !header.isBlank() && header.length() <= MAX_LENGTH;
    }

    private static String hash(CachedBodyRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.getMethod() + " " + request.getRequestURI() + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(request.body());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
