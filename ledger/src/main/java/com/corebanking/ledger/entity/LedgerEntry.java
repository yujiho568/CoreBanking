package com.corebanking.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_entries_account_created_at", columnList = "account_id, created_at DESC")
        }
)
public class LedgerEntry {

    @Id
    @Column(name = "entry_id", nullable = false, updatable = false, length = 36)
    private String entryId;

    @Column(nullable = false, updatable = false, length = 36)
    private String transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private EntryType entryType;

    @Column(nullable = false, updatable = false, length = 40)
    private String accountId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry debit(String transferId, String accountId, BigDecimal amount, BigDecimal balanceAfter) {
        return create(transferId, accountId, amount, balanceAfter, EntryType.DEBIT);
    }

    public static LedgerEntry credit(String transferId, String accountId, BigDecimal amount, BigDecimal balanceAfter) {
        return create(transferId, accountId, amount, balanceAfter, EntryType.CREDIT);
    }

    private static LedgerEntry create(
            String transferId,
            String accountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            EntryType entryType
    ) {
        LedgerEntry entry = new LedgerEntry();
        entry.entryId = UUID.randomUUID().toString();
        entry.transferId = transferId;
        entry.accountId = accountId;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.entryType = entryType;
        entry.createdAt = Instant.now();
        return entry;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getTransferId() {
        return transferId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
