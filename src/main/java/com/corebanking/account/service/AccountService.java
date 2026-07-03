package com.corebanking.account.service;

import com.corebanking.account.dto.AccountTransferResult;
import com.corebanking.account.entity.Account;
import com.corebanking.account.entity.AccountStatus;
import com.corebanking.account.exception.AccountNotFoundException;
import com.corebanking.account.exception.InsufficientBalanceException;
import com.corebanking.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AccountService {

    // DB에서 계좌를 조회하는 Repository
    private final AccountRepository accountRepository;
    // 설정값에 따라 낙관적 락 또는 비관적 락 조회 방식을 선택한다.
    private final AccountLockProperties lockProperties;

    public AccountService(AccountRepository accountRepository, AccountLockProperties lockProperties) {
        this.accountRepository = accountRepository;
        this.lockProperties = lockProperties;
    }

    // 계좌 조회 API에서 사용하는 읽기 전용 조회 메서드
    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    // 송금 전에 출금 계좌의 사용 가능 금액을 예약한다.
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

    // 예약된 출금 금액을 실제 잔액에서 차감하고, 입금 계좌에 금액을 더한다.
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

    // 계좌 응답 DTO에서 사용할 수 있도록 사용 가능 잔액을 계산한다.
    public BigDecimal calculateAvailableBalance(Account account) {
        return account.getBalance().subtract(account.getReservedBalance());
    }

    // 계좌가 반드시 있어야 하는 경우에 사용한다. 없으면 도메인 예외를 던진다.
    private Account getRequiredAccount(String accountId) {
        return findByConfiguredLockMode(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    // 설정된 락 모드에 따라 일반 조회 또는 SELECT FOR UPDATE 조회를 선택한다.
    private Optional<Account> findByConfiguredLockMode(String accountId) {
        if (lockProperties.getLockMode() == AccountLockMode.PESSIMISTIC) {
            return accountRepository.findByIdForUpdate(accountId);
        }
        return accountRepository.findById(accountId);
    }

    // 닫힌 계좌에서는 송금 관련 잔액 변경을 할 수 없다.
    private void validateActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getAccountId());
        }
    }

    // 송금 금액은 null이 아니고 0보다 커야 한다.
    private void validatePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
