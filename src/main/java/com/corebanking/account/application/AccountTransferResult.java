package com.corebanking.account.application;

import java.math.BigDecimal;

public record AccountTransferResult(
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter
) {
}
