package com.corebanking.account.dto;

import java.math.BigDecimal;

public record AccountTransferResult(
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter
) {
}
