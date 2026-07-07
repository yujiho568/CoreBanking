package com.corebanking.account.port;

import java.math.BigDecimal;

public interface AccountTransferPort {

    void reserve(String accountId, BigDecimal amount);

    AccountTransferResult commitTransfer(String fromAccountId, String toAccountId, BigDecimal amount);
}
