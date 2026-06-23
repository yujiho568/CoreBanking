package com.corebanking.transfer.application;

import java.math.BigDecimal;

public record TransferCommand(
        String idempotencyKey,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount
) {
}
