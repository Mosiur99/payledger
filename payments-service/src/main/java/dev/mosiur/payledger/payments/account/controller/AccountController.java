package dev.mosiur.payledger.payments.account.controller;

import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.service.AccountService;
import dev.mosiur.payledger.payments.common.security.CallerId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(
            @CallerId String ownerId,
            @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse account = accountService.open(ownerId, request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
    }

    @GetMapping
    public List<AccountResponse> list(@CallerId String ownerId) {
        return accountService.listForOwner(ownerId);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(
            @CallerId String ownerId,
            @PathVariable UUID accountId) {
        return accountService.getForOwner(ownerId, accountId);
    }
}
