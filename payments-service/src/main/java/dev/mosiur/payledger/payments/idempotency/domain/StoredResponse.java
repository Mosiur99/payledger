package dev.mosiur.payledger.payments.idempotency.domain;

public record StoredResponse(int status, String contentType, String body) {
}
