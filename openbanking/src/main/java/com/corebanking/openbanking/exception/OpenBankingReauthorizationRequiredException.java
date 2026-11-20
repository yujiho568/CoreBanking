package com.corebanking.openbanking.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class OpenBankingReauthorizationRequiredException extends CoreBankingException {
    public OpenBankingReauthorizationRequiredException() {
        super("OPEN_BANKING_REAUTHORIZATION_REQUIRED", HttpStatus.UNAUTHORIZED,
                "The Open Banking connection must be authorized again.");
    }
}
