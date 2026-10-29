package com.corebanking.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

@Service
public class CacheInvalidationWorker {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationWorker.class);
    private final CacheInvalidationRepository repository;
    private final CacheManager manager;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redis;
    public CacheInvalidationWorker(CacheInvalidationRepository repository, CacheManager manager, org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redis) {
        this.repository = repository; this.manager = manager; this.redis = redis;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String id) {
        var job = repository.lock(id).orElseThrow();
        if (job.isCompleted() || job.getNextAttempt().isAfter(Instant.now())) return;
        try {
            // Delete legacy keys and scan the targeted account namespace, including every normalized limit.
            Cache accounts = manager.getCache("accounts");
            Cache ledger = manager.getCache("accountLedgerEntries");
            if (accounts == null || ledger == null) throw new IllegalStateException("Required cache is missing");
            accounts.evict(job.getAccountId());
            for (int limit = 1; limit <= 100; limit++) ledger.evict(job.getAccountId() + ":" + limit);
            if (manager instanceof org.springframework.data.redis.cache.RedisCacheManager) {
                var template = redis.getObject();
                String escaped = AccountCache.namespace(job.getAccountId());
                for (String name : new String[]{"accounts", "accountLedgerEntries"}) {
                    template.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                        var options = org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(name + "::" + escaped + ":v*").count(100).build();
                        try (var cursor = connection.scan(options)) {
                            while (cursor.hasNext()) {
                                Long acknowledged = connection.del(cursor.next());
                                if (acknowledged == null) throw new IllegalStateException("Missing Redis deletion acknowledgement");
                            }
                        }
                        return null;
                    });
                }
            }
            job.complete();
        } catch (RuntimeException failure) {
            job.failed(failure);
            log.warn("Cache invalidation pending: id={}, account={}, failures={}", id, job.getAccountId(), job.getFailures(), failure);
        }
    }
}
