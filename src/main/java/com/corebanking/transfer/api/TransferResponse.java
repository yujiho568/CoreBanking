package com.corebanking.transfer.api;

import com.corebanking.transfer.domain.Transfer;
import com.corebanking.transfer.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String transferId,
        String idempotencyKey,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        TransferStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getTransferId(),
                transfer.getIdempotencyKey(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }
}
