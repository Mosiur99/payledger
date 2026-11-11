package dev.mosiur.payledger.payments.transfer.controller;

import dev.mosiur.payledger.payments.common.security.CallerId;
import dev.mosiur.payledger.payments.common.web.Idempotent;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.model.TransferResponse;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @CallerId String ownerId,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(ownerId, request);
    }
}
