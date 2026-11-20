package com.corebanking.openbanking.dto;

import com.corebanking.openbanking.entity.ExternalAccount;
import com.corebanking.openbanking.port.BalanceInfo;

import java.math.BigDecimal;
import java.time.Instant;

public record ExternalBalanceResponse(
        String externalAccountId,
        String bankName,
        String maskedAccountNumber,
        BigDecimal balance,
        BigDecimal availableAmount,
        String accountType,
        String productName,
        String lastTransactionDate,
        String providerTransactionId,
        Instant fetchedAt
) {
    public static ExternalBalanceResponse of(ExternalAccount account, BalanceInfo balance) {
        return new ExternalBalanceResponse(account.getExternalAccountId(), balance.bankName(),
                account.getMaskedAccountNumber(), balance.balance(), balance.availableAmount(),
                balance.accountType(), balance.productName(), balance.lastTransactionDate(),
                balance.apiTransactionId(), balance.fetchedAt());
    }
}
