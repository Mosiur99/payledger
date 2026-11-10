package dev.mosiur.payledger.payments.common.web;

/** Temporary caller identity until JWT authentication replaces it. */
public final class CallerHeader {

    public static final String NAME = "X-Owner-Id";

    private CallerHeader() {
    }
}
