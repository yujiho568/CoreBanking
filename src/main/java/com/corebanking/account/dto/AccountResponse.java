package com.corebanking.account.dto;

import com.corebanking.account.entity.Account;
import com.corebanking.account.entity.AccountStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record AccountResponse(
        String accountId,
        String ownerName,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        AccountStatus status,
        Long version
) implements Serializable {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getOwnerName(),
                account.getBalance(),
                account.getReservedBalance(),
                account.getBalance().subtract(account.getReservedBalance()),
                account.getStatus(),
                account.getVersion()
        );
    }
}
