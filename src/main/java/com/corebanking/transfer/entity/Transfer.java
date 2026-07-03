package com.corebanking.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "transfers",
        uniqueConstraints = @UniqueConstraint(name = "uk_transfers_idempotency_key", columnNames = "idempotency_key")
)
public class Transfer {

    @Id
    @Column(name = "transfer_id", nullable = false, updatable = false, length = 36)
    private String transferId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 40)
    private String fromAccountId;

    @Column(nullable = false, length = 40)
    private String toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Transfer() {
    }

    public static Transfer requested(
            String transferId,
            String idempotencyKey,
            String fromAccountId,
            String toAccountId,
            BigDecimal amount
    ) {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }
        Transfer transfer = new Transfer();
        transfer.transferId = transferId;
        transfer.idempotencyKey = idempotencyKey;
        transfer.fromAccountId = fromAccountId;
        transfer.toAccountId = toAccountId;
        transfer.amount = amount;
        transfer.status = TransferStatus.REQUESTED;
        transfer.createdAt = Instant.now();
        transfer.updatedAt = transfer.createdAt;
        return transfer;
    }

    public void markProcessing() {
        status = TransferStatus.PROCESSING;
        updatedAt = Instant.now();
    }

    public void complete() {
        status = TransferStatus.COMPLETED;
        updatedAt = Instant.now();
    }

    public String getTransferId() {
        return transferId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
