package com.corebanking.account.application;

import java.math.BigDecimal;

public interface AccountOperations {

    void reserve(String accountId, BigDecimal amount);

    AccountTransferResult commitTransfer(String fromAccountId, String toAccountId, BigDecimal amount);
}
