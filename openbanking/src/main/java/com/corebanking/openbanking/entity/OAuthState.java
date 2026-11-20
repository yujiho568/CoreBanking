package com.corebanking.openbanking.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "open_banking_oauth_states")
public class OAuthState {
    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String state;

    @Column(name = "owner_id", length = 80, nullable = false, updatable = false)
    private String ownerId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    protected OAuthState() {
    }

    public OAuthState(String state, String ownerId, Instant expiresAt) {
        this.state = state;
        this.ownerId = ownerId;
        this.expiresAt = expiresAt;
    }

    public boolean canConsume(Instant now) {
        return !used && expiresAt.isAfter(now);
    }

    public void consume() { used = true; }
    public String getState() { return state; }
    public String getOwnerId() { return ownerId; }
}
