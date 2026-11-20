package com.corebanking.openbanking.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalTransactionInfo(
        LocalDateTime transactionAt,
        String inOutType,
        String transactionType,
        String description,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String branchName
) {
}
