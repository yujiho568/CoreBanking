package com.corebanking.openbanking.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class InvalidOAuthStateException extends CoreBankingException {
    public InvalidOAuthStateException() {
        super("INVALID_OAUTH_STATE", HttpStatus.BAD_REQUEST, "OAuth state is invalid, expired, or already used.");
    }
}
