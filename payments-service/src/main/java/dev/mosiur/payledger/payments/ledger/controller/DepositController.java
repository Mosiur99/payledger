package dev.mosiur.payledger.payments.ledger.controller;

import dev.mosiur.payledger.payments.common.security.CallerId;
import dev.mosiur.payledger.payments.common.web.Idempotent;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.model.TransactionResponse;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deposits")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(
            @CallerId String admin,
            @Valid @RequestBody DepositRequest request) {
        return depositService.deposit(admin, request);
    }
}
