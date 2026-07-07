package com.corebanking.ledger.port;

import java.math.BigDecimal;

public interface LedgerRecordPort {

    void record(
            String fromAccountId,
            String toAccountId,
            BigDecimal amount,
            String transferId,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter
    );
}
