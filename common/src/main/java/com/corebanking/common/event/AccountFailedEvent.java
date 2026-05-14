package com.corebanking.common.event;

import java.time.Instant;
import java.util.UUID;

public record AccountFailedEvent(
        String eventId,
        String transferId,
        String fromAccountId,
        String reason,
        Instant occurredAt
) {
    public static AccountFailedEvent of(String transferId, String fromAccountId, String reason) {
        return new AccountFailedEvent(UUID.randomUUID().toString(), transferId,
                fromAccountId, reason, Instant.now());
    }
}
