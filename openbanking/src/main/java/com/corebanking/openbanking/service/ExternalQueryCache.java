package com.corebanking.openbanking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Shared Redis results, per-process single flight and a fail-fast provider bulkhead. */
@Component
public class ExternalQueryCache {
    private final CacheManager manager;
    private final Semaphore permits;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> flights = new ConcurrentHashMap<>();

    public ExternalQueryCache(CacheManager manager,
            @Value("${core-banking.open-banking.max-concurrent-queries:16}") int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("concurrency must be positive");
        this.manager = manager;
        this.permits = new Semaphore(concurrency);
    }

    public static String key(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object part : parts) {
                byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @SuppressWarnings("unchecked")
    public <T> T read(String name, String key, boolean refresh, Supplier<T> loader) {
        Cache cache;
        try { cache = manager.getCache(name); } catch (RuntimeException unavailable) { cache = null; }
        // Only fresh loads enter the flight map, so refresh never joins a cached response.
        if (!refresh && cache != null) {
            try {
                Cache.ValueWrapper hit = cache.get(key);
                if (hit != null) return (T) hit.get();
            } catch (RuntimeException unavailable) { /* Provider remains authoritative. */ }
        }
        String flightKey = name + ":" + key;
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> active = flights.putIfAbsent(flightKey, mine);
        if (active != null) return (T) await(active);
        try {
            if (!permits.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "External query capacity exceeded; retry later.");
            T value;
            try { value = loader.get(); } finally { permits.release(); }
            if (cache != null) {
                try { cache.put(key, value); } catch (RuntimeException unavailable) { /* Return fresh result. */ }
            }
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error failure) {
            mine.completeExceptionally(failure);
            throw failure;
        } finally { flights.remove(flightKey, mine); }
    }

    private Object await(CompletableFuture<Object> future) {
        try { return future.get(120, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted", interrupted);
        } catch (TimeoutException timeout) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "External query wait timed out", timeout);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failure.getCause());
        }
    }
}
