package com.corebanking.openbanking.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class OpenBankingProviderException extends CoreBankingException {
    public OpenBankingProviderException(String message) {
        super("OPEN_BANKING_PROVIDER_ERROR", HttpStatus.BAD_GATEWAY, message);
    }

    public static OpenBankingProviderException timeout() {
        return new OpenBankingProviderException("Open Banking did not respond within the configured timeout.");
    }
}
