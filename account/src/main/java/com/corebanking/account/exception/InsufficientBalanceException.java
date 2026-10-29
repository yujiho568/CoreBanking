package com.corebanking.account.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientBalanceException extends CoreBankingException {

    public InsufficientBalanceException(String accountId, BigDecimal available, BigDecimal required) {
        super("INSUFFICIENT_BALANCE", HttpStatus.CONFLICT, "Insufficient balance. accountId=" + accountId
                + " available=" + available
                + " required=" + required);
    }
}
