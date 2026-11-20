package com.corebanking.openbanking.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "open_banking_connections", uniqueConstraints = {
        @UniqueConstraint(name = "uk_open_banking_owner_user", columnNames = {"owner_id", "user_seq_no"})
})
public class OpenBankingConnection {
    @Id
    @Column(name = "connection_id", length = 36, nullable = false, updatable = false)
    private String connectionId;

    @Column(name = "owner_id", length = 80, nullable = false, updatable = false)
    private String ownerId;

    @Column(name = "user_seq_no", length = 32, nullable = false, updatable = false)
    private String userSeqNo;

    @Lob
    @Column(name = "access_token_ciphertext", nullable = false)
    private String accessTokenCiphertext;

    @Lob
    @Column(name = "refresh_token_ciphertext")
    private String refreshTokenCiphertext;

    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    @Column(length = 200)
    private String scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OpenBankingConnectionStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected OpenBankingConnection() {
    }

    public static OpenBankingConnection connect(
            String ownerId, String userSeqNo, String accessTokenCiphertext,
            String refreshTokenCiphertext, Instant tokenExpiresAt, String scopes
    ) {
        OpenBankingConnection connection = new OpenBankingConnection();
        connection.connectionId = UUID.randomUUID().toString();
        connection.ownerId = ownerId;
        connection.userSeqNo = userSeqNo;
        connection.updateTokens(accessTokenCiphertext, refreshTokenCiphertext, tokenExpiresAt, scopes);
        connection.createdAt = Instant.now();
        return connection;
    }

    public void updateTokens(String accessTokenCiphertext, String refreshTokenCiphertext,
                             Instant tokenExpiresAt, String scopes) {
        this.accessTokenCiphertext = accessTokenCiphertext;
        if (refreshTokenCiphertext != null && !refreshTokenCiphertext.isBlank()) {
            this.refreshTokenCiphertext = refreshTokenCiphertext;
        }
        this.tokenExpiresAt = tokenExpiresAt;
        this.scopes = scopes;
        this.status = OpenBankingConnectionStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void requireReauthorization() {
        status = OpenBankingConnectionStatus.REAUTHORIZATION_REQUIRED;
        updatedAt = Instant.now();
    }

    public String getConnectionId() { return connectionId; }
    public String getOwnerId() { return ownerId; }
    public String getUserSeqNo() { return userSeqNo; }
    public String getAccessTokenCiphertext() { return accessTokenCiphertext; }
    public String getRefreshTokenCiphertext() { return refreshTokenCiphertext; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public String getScopes() { return scopes; }
    public OpenBankingConnectionStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
