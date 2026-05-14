package com.corebanking.transfer.domain;

public enum TransferStatus {
    PENDING,
    ACCOUNT_CHECKING,
    LEDGER_PROCESSING,
    COMPLETED,
    CANCELLED
}
