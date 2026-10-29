package com.corebanking.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.function.Supplier;

@Service
public class AccountCache {
    private final CacheStateReader states;
    private final CacheManager manager;
    public AccountCache(CacheStateReader states, CacheManager manager) { this.states = states; this.manager = manager; }

    static String namespace(String account) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(account.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // A reader that started before a transfer can only populate its old generation.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public <T> T read(String name, String account, String suffix, Supplier<T> database) {
        if (manager instanceof org.springframework.cache.support.NoOpCacheManager) return database.get();
        var before = states.read(account);
        if (before.pending()) return database.get();
        String key = namespace(account) + ":v" + before.generation() + suffix;
        Cache cache = manager.getCache(name);
        if (cache == null) return database.get();
        try {
            Cache.ValueWrapper cached = cache.get(key);
            if (cached != null) return (T) cached.get();
        } catch (RuntimeException unavailable) { return database.get(); }
        T value = database.get();
        var after = states.read(account);
        if (before.equals(after)) {
            try { cache.put(key, value); } catch (RuntimeException unavailable) { /* DB remains authoritative. */ }
        }
        return value;
    }
}
