package com.corebanking.openbanking.dto;

import com.corebanking.openbanking.entity.ExternalAccount;

import java.time.Instant;

public record ExternalAccountResponse(
        String externalAccountId,
        String bankCode,
        String bankName,
        String accountAlias,
        String maskedAccountNumber,
        boolean inquiryAgreed,
        Instant synchronizedAt
) {
    public static ExternalAccountResponse from(ExternalAccount account) {
        return new ExternalAccountResponse(account.getExternalAccountId(), account.getBankCode(),
                account.getBankName(), account.getAccountAlias(), account.getMaskedAccountNumber(),
                account.isInquiryAgreed(), account.getUpdatedAt());
    }
}
