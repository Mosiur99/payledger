package dev.mosiur.payledger.payments.transfer.controller;

import dev.mosiur.payledger.payments.common.web.CallerHeader;
import dev.mosiur.payledger.payments.common.web.Idempotent;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.model.TransferResponse;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader(CallerHeader.NAME) @NotBlank @Size(max = 64) String ownerId,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(ownerId, request);
    }
}
