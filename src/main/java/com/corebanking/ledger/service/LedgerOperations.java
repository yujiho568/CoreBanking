package com.corebanking.ledger.service;

import java.math.BigDecimal;

public interface LedgerOperations {

    void record(
            String fromAccountId,
            String toAccountId,
            BigDecimal amount,
            String transferId,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter
    );
}
