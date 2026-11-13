package dev.mosiur.payledger.payments.common.error;

import dev.mosiur.payledger.payments.account.domain.InsufficientFundsException;
import dev.mosiur.payledger.payments.account.service.AccountFrozenException;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import dev.mosiur.payledger.payments.ledger.service.CurrencyMismatchException;
import dev.mosiur.payledger.payments.transfer.service.SameAccountTransferException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every error into an RFC 9457 Problem Details response. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(ProblemType.ACCOUNT_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccountFrozenException.class)
    ProblemDetail accountFrozen(AccountFrozenException e) {
        return problem(ProblemType.ACCOUNT_FROZEN, e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        ProblemDetail problem = problem(ProblemType.INSUFFICIENT_FUNDS, e.getMessage());
        problem.setProperty("accountId", e.getAccountId());
        problem.setProperty("availableMinor", e.getAvailableMinor());
        problem.setProperty("requestedMinor", e.getRequestedMinor());
        return problem;
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail currencyMismatch(CurrencyMismatchException e) {
        return problem(ProblemType.CURRENCY_MISMATCH, e.getMessage());
    }

    @ExceptionHandler(SameAccountTransferException.class)
    ProblemDetail sameAccountTransfer(SameAccountTransferException e) {
        return problem(ProblemType.SAME_ACCOUNT_TRANSFER, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(ProblemType.INTERNAL, ProblemErrorController.INTERNAL_DETAIL);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        ProblemDetail problem = problem(ProblemType.VALIDATION, "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::errorsOf)
                .toList();
        ProblemDetail problem = problem(ProblemType.VALIDATION, "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Gives Spring's own 4xx errors (unreadable JSON, missing header, wrong type) our validation type. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem && status.value() == HttpStatus.BAD_REQUEST.value()) {
            problem.setType(ProblemType.VALIDATION.type());
            problem.setTitle(ProblemType.VALIDATION.title());
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    private static ProblemDetail problem(ProblemType type, String detail) {
        return type.problem(detail);
    }

    /** A request body reports its own fields; a header or query parameter reports the parameter name. */
    private static Stream<Map<String, String>> errorsOf(ParameterValidationResult result) {
        if (result instanceof ParameterErrors bodyErrors) {
            return bodyErrors.getFieldErrors().stream().map(GlobalExceptionHandler::fieldError);
        }
        String parameter = String.valueOf(result.getMethodParameter().getParameterName());
        return result.getResolvableErrors().stream()
                .map(error -> Map.of("field", parameter, "message", String.valueOf(error.getDefaultMessage())));
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }
}
