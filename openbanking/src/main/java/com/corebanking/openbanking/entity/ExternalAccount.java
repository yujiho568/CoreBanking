package com.corebanking.openbanking.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_external_account_connection_fintech", columnNames = {"connection_id", "fintech_use_num_hash"})
}, indexes = @Index(name = "idx_external_accounts_owner", columnList = "owner_id"))
public class ExternalAccount {
    @Id
    @Column(name = "external_account_id", length = 36, nullable = false, updatable = false)
    private String externalAccountId;

    @Column(name = "connection_id", length = 36, nullable = false, updatable = false)
    private String connectionId;

    @Column(name = "owner_id", length = 80, nullable = false, updatable = false)
    private String ownerId;

    @Lob
    @Column(name = "fintech_use_num_ciphertext", nullable = false)
    private String fintechUseNumCiphertext;

    @Column(name = "fintech_use_num_hash", length = 64, nullable = false, updatable = false)
    private String fintechUseNumHash;

    @Column(name = "bank_code", length = 3)
    private String bankCode;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_alias", length = 100)
    private String accountAlias;

    @Column(name = "masked_account_number", length = 40)
    private String maskedAccountNumber;

    @Column(name = "inquiry_agreed", nullable = false)
    private boolean inquiryAgreed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ExternalAccount() {
    }

    public static ExternalAccount connect(
            String connectionId, String ownerId, String fintechUseNumCiphertext,
            String fintechUseNumHash, String bankCode, String bankName,
            String accountAlias, String maskedAccountNumber, boolean inquiryAgreed
    ) {
        ExternalAccount account = new ExternalAccount();
        account.externalAccountId = UUID.randomUUID().toString();
        account.connectionId = connectionId;
        account.ownerId = ownerId;
        account.fintechUseNumCiphertext = fintechUseNumCiphertext;
        account.fintechUseNumHash = fintechUseNumHash;
        account.createdAt = Instant.now();
        account.updateMetadata(bankCode, bankName, accountAlias, maskedAccountNumber, inquiryAgreed);
        return account;
    }

    public void updateMetadata(String bankCode, String bankName, String accountAlias,
                               String maskedAccountNumber, boolean inquiryAgreed) {
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountAlias = accountAlias;
        this.maskedAccountNumber = maskedAccountNumber;
        this.inquiryAgreed = inquiryAgreed;
        this.updatedAt = Instant.now();
    }

    public String getExternalAccountId() { return externalAccountId; }
    public String getConnectionId() { return connectionId; }
    public String getOwnerId() { return ownerId; }
    public String getFintechUseNumCiphertext() { return fintechUseNumCiphertext; }
    public String getFintechUseNumHash() { return fintechUseNumHash; }
    public String getBankCode() { return bankCode; }
    public String getBankName() { return bankName; }
    public String getAccountAlias() { return accountAlias; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public boolean isInquiryAgreed() { return inquiryAgreed; }
    public Instant getUpdatedAt() { return updatedAt; }
}
