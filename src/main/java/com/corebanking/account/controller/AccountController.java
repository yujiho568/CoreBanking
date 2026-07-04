package com.corebanking.account.controller;

import com.corebanking.account.dto.AccountResponse;
import com.corebanking.account.entity.Account;
import com.corebanking.account.service.AccountReadService;
import com.corebanking.ledger.dto.LedgerEntryResponse;
import com.corebanking.ledger.service.LedgerReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountReadService accountReadService;
    private final LedgerReadService ledgerReadService;

    public AccountController(AccountReadService accountReadService, LedgerReadService ledgerReadService) {
        this.accountReadService = accountReadService;
        this.ledgerReadService = ledgerReadService;
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        Account account = accountReadService.getAccount(accountId);
        return AccountResponse.from(account);
    }

    @GetMapping("/{accountId}/ledger-entries")
    public List<LedgerEntryResponse> getAccountLedgerEntries(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ledgerReadService.getAccountLedgerEntries(accountId, limit)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
