package dev.mosiur.payledger.payments.common.error;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Every error the API can return. The type URI is stable, so clients can switch on it. */
public enum ProblemType {

    VALIDATION("validation", "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED("unauthenticated", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("forbidden", "Access denied", HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_FOUND("account-not-found", "Account not found", HttpStatus.NOT_FOUND),
    ACCOUNT_FROZEN("account-frozen", "Account frozen", HttpStatus.CONFLICT),
    INSUFFICIENT_FUNDS("insufficient-funds", "Insufficient funds", HttpStatus.UNPROCESSABLE_CONTENT),
    CURRENCY_MISMATCH("currency-mismatch", "Currency mismatch", HttpStatus.UNPROCESSABLE_CONTENT),
    SAME_ACCOUNT_TRANSFER("same-account-transfer", "Same account transfer", HttpStatus.UNPROCESSABLE_CONTENT),
    IDEMPOTENCY_KEY_REQUIRED("idempotency-key-required", "Idempotency key required", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused", "Idempotency key reused", HttpStatus.UNPROCESSABLE_CONTENT),
    IDEMPOTENCY_IN_PROGRESS("idempotency-in-progress", "Request in progress", HttpStatus.CONFLICT),
    INTERNAL("internal", "Internal error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final URI type;
    private final String title;
    private final HttpStatus status;

    ProblemType(String slug, String title, HttpStatus status) {
        this.type = URI.create("/problems/" + slug);
        this.title = title;
        this.status = status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }

    public ProblemDetail problem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        return problem;
    }
}
