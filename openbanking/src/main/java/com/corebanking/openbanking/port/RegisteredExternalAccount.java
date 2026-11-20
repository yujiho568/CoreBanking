package com.corebanking.openbanking.port;

public record RegisteredExternalAccount(
        String fintechUseNum,
        String bankCode,
        String bankName,
        String accountAlias,
        String maskedAccountNumber,
        boolean inquiryAgreed
) {
}
