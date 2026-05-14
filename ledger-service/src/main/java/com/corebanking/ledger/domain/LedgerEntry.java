package com.corebanking.ledger.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// append-only 원장 엔트리 — 한 번 기록하면 수정/삭제 없음
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @Column(name = "entry_id", nullable = false, updatable = false)
    private String entryId;

    @Column(nullable = false)
    private String transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static LedgerEntry debit(String transferId, String accountId, BigDecimal amount) {
        LedgerEntry e = new LedgerEntry();
        e.entryId = UUID.randomUUID().toString();
        e.transferId = transferId;
        e.entryType = EntryType.DEBIT;
        e.accountId = accountId;
        e.amount = amount;
        e.createdAt = Instant.now();
        return e;
    }

    public static LedgerEntry credit(String transferId, String accountId, BigDecimal amount) {
        LedgerEntry e = new LedgerEntry();
        e.entryId = UUID.randomUUID().toString();
        e.transferId = transferId;
        e.entryType = EntryType.CREDIT;
        e.accountId = accountId;
        e.amount = amount;
        e.createdAt = Instant.now();
        return e;
    }
}
