package com.corebanking.account.api;

import com.corebanking.account.domain.Account;
import com.corebanking.account.domain.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
        String accountId,
        String ownerName,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        AccountStatus status,
        Long version
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getOwnerName(),
                account.getBalance(),
                account.getReservedBalance(),
                account.availableBalance(),
                account.getStatus(),
                account.getVersion()
        );
    }
}
