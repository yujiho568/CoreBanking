package com.corebanking.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerRecordedEvent(
        String eventId,
        String transferId,
        BigDecimal amount,
        Instant occurredAt
) {
    public static LedgerRecordedEvent of(String transferId, BigDecimal amount) {
        return new LedgerRecordedEvent(UUID.randomUUID().toString(), transferId, amount, Instant.now());
    }
}
