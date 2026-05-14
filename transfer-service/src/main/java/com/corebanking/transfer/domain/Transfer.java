package com.corebanking.transfer.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private String transferId;

    @Column(nullable = false)
    private String fromAccountId;

    @Column(nullable = false)
    private String toAccountId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    public static Transfer create(String transferId, String fromAccountId,
                                   String toAccountId, BigDecimal amount) {
        Transfer t = new Transfer();
        t.transferId = transferId;
        t.fromAccountId = fromAccountId;
        t.toAccountId = toAccountId;
        t.amount = amount;
        t.status = TransferStatus.PENDING;
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    public void markAccountChecking() {
        this.status = TransferStatus.ACCOUNT_CHECKING;
        this.updatedAt = Instant.now();
    }

    public void markLedgerProcessing() {
        this.status = TransferStatus.LEDGER_PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = TransferStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = TransferStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }
}
