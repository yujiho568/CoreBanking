package com.corebanking.account.api;

import com.corebanking.account.application.AccountQueryService;
import com.corebanking.account.domain.Account;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountQueryService accountQueryService;

    public AccountController(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        Account account = accountQueryService.getAccount(accountId);
        return AccountResponse.from(account);
    }
}
