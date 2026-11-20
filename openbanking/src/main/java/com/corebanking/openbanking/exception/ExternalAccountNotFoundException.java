package com.corebanking.openbanking.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class ExternalAccountNotFoundException extends CoreBankingException {
    public ExternalAccountNotFoundException(String id) {
        super("EXTERNAL_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "External account not found: " + id);
    }
}
