package com.corebanking.common.cache;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cache_invalidations", indexes = {
    @Index(name = "idx_invalidation_account", columnList = "account_id,completed"),
    @Index(name = "idx_invalidation_due", columnList = "completed,next_attempt")
})
public class CacheInvalidation {
    @Id private String id;
    @Column(name = "account_id", nullable = false, length = 40) private String accountId;
    @Column(nullable = false) private boolean completed;
    @Column(nullable = false) private int failures;
    @Column(name = "next_attempt", nullable = false) private Instant nextAttempt;
    @Column(name = "last_error", length = 500) private String lastError;
    protected CacheInvalidation() {}
    public CacheInvalidation(String accountId) {
        this.id = UUID.randomUUID().toString(); this.accountId = accountId; this.nextAttempt = Instant.now();
    }
    public String getId() { return id; }
    public String getAccountId() { return accountId; }
    public boolean isCompleted() { return completed; }
    public Instant getNextAttempt() { return nextAttempt; }
    public int getFailures() { return failures; }
    public void complete() { completed = true; lastError = null; }
    public void failed(RuntimeException failure) {
        failures = Math.min(failures + 1, 1000000);
        nextAttempt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(failures, 8)));
        String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        lastError = message.substring(0, Math.min(500, message.length()));
    }
}
