package com.corebanking.account.controller;

import com.corebanking.account.dto.AccountResponse;
import com.corebanking.account.service.AccountReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountReadService accountReadService;

    public AccountController(AccountReadService accountReadService) {
        this.accountReadService = accountReadService;
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        return accountReadService.getAccountResponse(accountId);
    }
}
