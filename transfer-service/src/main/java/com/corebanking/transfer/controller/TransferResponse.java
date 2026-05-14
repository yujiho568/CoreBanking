package com.corebanking.transfer.controller;

import com.corebanking.transfer.domain.Transfer;
import com.corebanking.transfer.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String transferId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        TransferStatus status,
        String failureReason,
        Instant createdAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getTransferId(),
                t.getFromAccountId(),
                t.getToAccountId(),
                t.getAmount(),
                t.getStatus(),
                t.getFailureReason(),
                t.getCreatedAt()
        );
    }
}
