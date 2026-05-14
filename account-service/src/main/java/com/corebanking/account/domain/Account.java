package com.corebanking.account.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    // 낙관적 락 — 동시 이체 요청이 같은 계좌를 동시에 수정하면 OptimisticLockingFailureException 발생
    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Account create(String accountId, String ownerName, BigDecimal initialBalance) {
        Account a = new Account();
        a.accountId = accountId;
        a.ownerName = ownerName;
        a.balance = initialBalance;
        a.createdAt = Instant.now();
        return a;
    }

    public void reserve(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(accountId, balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void restore(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
