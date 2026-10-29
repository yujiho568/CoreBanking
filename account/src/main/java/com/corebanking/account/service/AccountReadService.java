package com.corebanking.account.service;

import com.corebanking.account.dto.AccountResponse;
import com.corebanking.account.entity.Account;
import com.corebanking.account.exception.AccountNotFoundException;
import com.corebanking.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountReadService {

    private final com.corebanking.common.cache.AccountCache cache;
    private final AccountRepository accountRepository;

    public AccountReadService(AccountRepository accountRepository, com.corebanking.common.cache.AccountCache cache) {
        this.cache = cache;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public AccountResponse getAccountResponse(String accountId) {
        return cache.read("accounts", accountId, "", () -> AccountResponse.from(getAccount(accountId)));
    }

    public BigDecimal calculateAvailableBalance(Account account) {
        return account.getBalance().subtract(account.getReservedBalance());
    }
}
