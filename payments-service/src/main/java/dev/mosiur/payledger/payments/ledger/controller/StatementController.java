package dev.mosiur.payledger.payments.ledger.controller;

import dev.mosiur.payledger.payments.common.security.CallerId;
import dev.mosiur.payledger.payments.ledger.model.StatementResponse;
import dev.mosiur.payledger.payments.ledger.service.StatementService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/api/v1/accounts/{accountId}/statement")
    public StatementResponse statement(
            @CallerId String ownerId,
            @PathVariable UUID accountId,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return statementService.statement(ownerId, accountId, before, limit);
    }
}
