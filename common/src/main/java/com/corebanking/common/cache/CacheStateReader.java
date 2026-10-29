package com.corebanking.common.cache;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CacheStateReader {
    private final CacheInvalidationRepository repository;
    public CacheStateReader(CacheInvalidationRepository repository) { this.repository = repository; }
    public record State(long generation, boolean pending) {}
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public State read(String account) {
        Object[] row = repository.state(account).get(0);
        return new State(((Number) row[0]).longValue(), ((Number) row[1]).longValue() > 0);
    }
}
