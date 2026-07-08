package com.corebanking.ledger.dto;

import com.corebanking.ledger.entity.EntryType;
import com.corebanking.ledger.entity.LedgerEntry;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        String entryId,
        String transferId,
        EntryType entryType,
        String accountId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) implements Serializable {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getEntryId(),
                entry.getTransferId(),
                entry.getEntryType(),
                entry.getAccountId(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getCreatedAt()
        );
    }
}
