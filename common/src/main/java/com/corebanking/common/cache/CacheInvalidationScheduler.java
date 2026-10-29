package com.corebanking.common.cache;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import org.slf4j.LoggerFactory;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "core-banking.cache.worker-enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class CacheInvalidationScheduler {
    private final CacheInvalidationRepository repository;
    private final CacheInvalidationWorker worker;
    public CacheInvalidationScheduler(CacheInvalidationRepository repository, CacheInvalidationWorker worker) {
        this.repository = repository; this.worker = worker;
    }
    @Scheduled(fixedDelayString = "${core-banking.cache.poll-ms:1000}")
    public void poll() {
        for (String id : repository.due(Instant.now(), PageRequest.of(0, 20))) {
            try { worker.process(id); }
            catch (RuntimeException failure) { LoggerFactory.getLogger(getClass()).warn("Invalidation transaction failed: {}", id, failure); }
        }
    }
}
