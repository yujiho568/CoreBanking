package com.corebanking.account.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.entity.AccountStatus;
import com.corebanking.account.exception.AccountNotFoundException;
import com.corebanking.account.exception.InsufficientBalanceException;
import com.corebanking.account.port.AccountTransferPort;
import com.corebanking.account.port.AccountTransferResult;
import com.corebanking.account.repository.AccountRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AccountWriteService implements AccountTransferPort {

    private final AccountRepository accountRepository;
    private final AccountLockProperties lockProperties;

    public AccountWriteService(AccountRepository accountRepository, AccountLockProperties lockProperties) {
        this.accountRepository = accountRepository;
        this.lockProperties = lockProperties;
    }

    @Override
    @CacheEvict(cacheNames = "accounts", key = "#accountId")
    public void reserve(String accountId, BigDecimal amount) {
        Account account = getRequiredAccount(accountId);
        validateActive(account);
        validatePositive(amount);

        BigDecimal availableBalance = calculateAvailableBalance(account);
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(accountId, availableBalance, amount);
        }

        account.changeReservedBalance(account.getReservedBalance().add(amount));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "accounts", key = "#fromAccountId"),
            @CacheEvict(cacheNames = "accounts", key = "#toAccountId")
    })
    public AccountTransferResult commitTransfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        Account from = getRequiredAccount(fromAccountId);
        Account to = getRequiredAccount(toAccountId);

        validateActive(from);
        validateActive(to);
        validatePositive(amount);

        if (from.getReservedBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Reserved balance is smaller than debit amount.");
        }

        from.changeReservedBalance(from.getReservedBalance().subtract(amount));
        from.changeBalance(from.getBalance().subtract(amount));
        to.changeBalance(to.getBalance().add(amount));

        return new AccountTransferResult(from.getBalance(), to.getBalance());
    }

    public BigDecimal calculateAvailableBalance(Account account) {
        return account.getBalance().subtract(account.getReservedBalance());
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

    private void validateActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getAccountId());
        }
    }

    private void validatePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
