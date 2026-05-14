package com.corebanking.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCancelledEvent(
        String eventId,
        String transferId,
        String fromAccountId,
        BigDecimal reservedAmount,
        String reason,
        Instant occurredAt
) {
    public static TransferCancelledEvent of(String transferId, String fromAccountId,
                                            BigDecimal reservedAmount, String reason) {
        return new TransferCancelledEvent(UUID.randomUUID().toString(), transferId,
                fromAccountId, reservedAmount, reason, Instant.now());
    }
}
