package com.corebanking.common.event;

import java.math.BigDecimal;

public record TransferCompletedEvent(
        String transferId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount
) {
}
