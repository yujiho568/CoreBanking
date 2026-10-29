package com.corebanking.transfer.exception;

import com.corebanking.common.exception.CoreBankingException;
import org.springframework.http.HttpStatus;

public class TransferNotFoundException extends CoreBankingException {

    public TransferNotFoundException(String transferId) {
        super("TRANSFER_NOT_FOUND", HttpStatus.NOT_FOUND, "Transfer not found: " + transferId);
    }
}
