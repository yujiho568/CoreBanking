package com.corebanking.openbanking.port;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceInfo(
        String apiTransactionId,
        String bankName,
        BigDecimal balance,
        BigDecimal availableAmount,
        String accountType,
        String productName,
        String lastTransactionDate,
        Instant fetchedAt
) {
}
