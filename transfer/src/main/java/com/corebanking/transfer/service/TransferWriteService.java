package com.corebanking.transfer.service;

import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.Transfer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferWriteService {
    private final TransferTransactionService transactions;
    public TransferWriteService(TransferTransactionService transactions) { this.transactions = transactions; }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transfer execute(CreateTransferRequest request) {
        try { return transactions.execute(request); }
        catch (DataIntegrityViolationException failure) {
            if (!isIdempotencyCollision(failure)) throw failure;
            return transactions.recover(request);
        }
    }

    static boolean isIdempotencyCollision(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null && name.replace("`", "").replace("'", "")
                        .matches("(?i)(.*\\.)?uk_transfers_idempotency_key")) return true;
            }
        }
        return false;
    }
}
