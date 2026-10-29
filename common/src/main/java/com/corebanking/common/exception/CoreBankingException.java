package com.corebanking.common.exception;

import org.springframework.http.HttpStatus;

public abstract class CoreBankingException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected CoreBankingException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
