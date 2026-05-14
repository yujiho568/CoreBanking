package com.corebanking.common.event;

import java.time.Instant;
import java.util.UUID;

public record LedgerFailedEvent(
        String eventId,
        String transferId,
        String reason,
        Instant occurredAt
) {
    public static LedgerFailedEvent of(String transferId, String reason) {
        return new LedgerFailedEvent(UUID.randomUUID().toString(), transferId, reason, Instant.now());
    }
}
