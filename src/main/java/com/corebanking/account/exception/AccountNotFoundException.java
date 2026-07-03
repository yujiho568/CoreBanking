package com.corebanking.account.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends CoreBankingException {

    public AccountNotFoundException(String accountId) {
        super("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "Account not found: " + accountId);
    }
}
