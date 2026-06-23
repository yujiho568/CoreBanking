package com.corebanking.account.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core-banking.account")
public class AccountLockProperties {

    private AccountLockMode lockMode = AccountLockMode.OPTIMISTIC;

    public AccountLockMode getLockMode() {
        return lockMode;
    }

    public void setLockMode(AccountLockMode lockMode) {
        this.lockMode = lockMode;
    }
}
