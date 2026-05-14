package com.corebanking.account.domain;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String accountId, BigDecimal balance, BigDecimal required) {
        super("잔액 부족 accountId=" + accountId + " balance=" + balance + " required=" + required);
    }
}
