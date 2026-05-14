package com.corebanking.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountReservedEvent(
        String eventId,
        String transferId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        Instant occurredAt
) {
    public static AccountReservedEvent of(String transferId, String fromAccountId,
                                          String toAccountId, BigDecimal amount) {
        return new AccountReservedEvent(UUID.randomUUID().toString(), transferId,
                fromAccountId, toAccountId, amount, Instant.now());
    }
}
