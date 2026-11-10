package dev.mosiur.payledger.payments.account.service;

import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import java.util.List;
import java.util.UUID;

public interface AccountService {

    AccountResponse open(String ownerId, OpenAccountRequest request);

    List<AccountResponse> listForOwner(String ownerId);

    AccountResponse getForOwner(String ownerId, UUID accountId);
}
