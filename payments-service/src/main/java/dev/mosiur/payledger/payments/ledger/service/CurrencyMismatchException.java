package dev.mosiur.payledger.payments.ledger.service;

import dev.mosiur.payledger.payments.account.domain.Currency;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency debit, Currency credit) {
        super("Cannot move money from a " + debit + " account to a " + credit + " account.");
    }
}
