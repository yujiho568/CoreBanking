package com.corebanking.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
        String eventId,
        String transferId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        Instant occurredAt
) {
    public static TransferCompletedEvent of(String transferId, String fromAccountId,
                                            String toAccountId, BigDecimal amount) {
        return new TransferCompletedEvent(UUID.randomUUID().toString(), transferId,
                fromAccountId, toAccountId, amount, Instant.now());
    }
}
