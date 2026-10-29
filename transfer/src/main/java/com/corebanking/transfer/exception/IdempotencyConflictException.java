package com.corebanking.transfer.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends CoreBankingException {
    public IdempotencyConflictException() {
        super("IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT, "Idempotency key has different contents or an unavailable result.");
    }
}
