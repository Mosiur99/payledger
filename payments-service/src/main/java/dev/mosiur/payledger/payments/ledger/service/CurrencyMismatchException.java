package dev.mosiur.payledger.payments.ledger.service;

import dev.mosiur.payledger.payments.account.domain.Currency;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency debit, Currency credit) {
        super("Cannot move money from " + debit + " to " + credit);
    }
}
