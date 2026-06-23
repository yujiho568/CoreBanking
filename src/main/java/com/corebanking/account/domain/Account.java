package com.corebanking.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false, length = 40)
    private String accountId;

    @Column(nullable = false, length = 100)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public static Account open(String accountId, String ownerName, BigDecimal initialBalance) {
        Account account = new Account();
        account.accountId = accountId;
        account.ownerName = ownerName;
        account.balance = initialBalance;
        account.reservedBalance = BigDecimal.ZERO;
        account.status = AccountStatus.ACTIVE;
        account.createdAt = Instant.now();
        account.updatedAt = account.createdAt;
        return account;
    }

    public void reserve(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (availableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(accountId, availableBalance(), amount);
        }
        reservedBalance = reservedBalance.add(amount);
        updatedAt = Instant.now();
    }

    public void commitReservedDebit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (reservedBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Reserved balance is smaller than debit amount.");
        }
        reservedBalance = reservedBalance.subtract(amount);
        balance = balance.subtract(amount);
        updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        balance = balance.add(amount);
        updatedAt = Instant.now();
    }

    public BigDecimal availableBalance() {
        return balance.subtract(reservedBalance);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + accountId);
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account account)) {
            return false;
        }
        return Objects.equals(accountId, account.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }
}
