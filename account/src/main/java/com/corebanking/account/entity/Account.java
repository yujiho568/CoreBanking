package com.corebanking.account.entity;

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

    public void changeReservedBalance(BigDecimal reservedBalance) {
        this.reservedBalance = reservedBalance;
        this.updatedAt = Instant.now();
    }

    public void changeBalance(BigDecimal balance) {
        this.balance = balance;
        this.updatedAt = Instant.now();
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
