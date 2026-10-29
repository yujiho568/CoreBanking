package com.corebanking.account.port;

import java.math.BigDecimal;

public record AccountTransferResult(
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter
) {
}
