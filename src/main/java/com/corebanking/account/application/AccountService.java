package com.corebanking.account.application;

import com.corebanking.account.domain.Account;
import com.corebanking.account.domain.AccountNotFoundException;
import com.corebanking.account.infrastructure.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AccountService implements AccountOperations {

    private final AccountRepository accountRepository;
    private final AccountLockProperties lockProperties;

    public AccountService(AccountRepository accountRepository, AccountLockProperties lockProperties) {
        this.accountRepository = accountRepository;
        this.lockProperties = lockProperties;
    }

    @Override
    public void reserve(String accountId, BigDecimal amount) {
        Account account = getRequiredAccount(accountId);
        account.reserve(amount);
    }

    @Override
    public AccountTransferResult commitTransfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        Account from = getRequiredAccount(fromAccountId);
        Account to = getRequiredAccount(toAccountId);

        from.commitReservedDebit(amount);
        to.credit(amount);
        return new AccountTransferResult(from.getBalance(), to.getBalance());
    }

    private Account getRequiredAccount(String accountId) {
        return findByConfiguredLockMode(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Optional<Account> findByConfiguredLockMode(String accountId) {
        if (lockProperties.getLockMode() == AccountLockMode.PESSIMISTIC) {
            return accountRepository.findByIdForUpdate(accountId);
        }
        return accountRepository.findById(accountId);
    }
}
