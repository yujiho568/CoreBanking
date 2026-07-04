package com.corebanking.account.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.exception.AccountNotFoundException;
import com.corebanking.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountReadService {

    // DB?먯꽌 怨꾩쥖瑜?議고쉶?섎뒗 Repository
    private final AccountRepository accountRepository;

    public AccountReadService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // 怨꾩쥖 議고쉶 API?먯꽌 ?ъ슜?섎뒗 ?쎄린 ?꾩슜 議고쉶 硫붿꽌??
    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    // 怨꾩쥖 ?묐떟 DTO?먯꽌 ?ъ슜?????덈룄濡??ъ슜 媛???붿븸??怨꾩궛?쒕떎.
    public BigDecimal calculateAvailableBalance(Account account) {
        return account.getBalance().subtract(account.getReservedBalance());
    }
}
