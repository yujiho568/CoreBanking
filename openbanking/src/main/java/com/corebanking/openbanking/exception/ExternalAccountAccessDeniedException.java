package com.corebanking.openbanking.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class ExternalAccountAccessDeniedException extends CoreBankingException {
    public ExternalAccountAccessDeniedException() {
        super("EXTERNAL_ACCOUNT_ACCESS_DENIED", HttpStatus.FORBIDDEN, "The external account belongs to another user.");
    }
}
