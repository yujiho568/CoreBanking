package com.corebanking.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AccountCacheTest {
    @Test void pendingWorkBypassesPreviouslyCachedValuesWithoutPopulating() {
        var states = mock(CacheStateReader.class);
        when(states.read("A")).thenReturn(new CacheStateReader.State(1, true));
        var manager = new ConcurrentMapCacheManager("accounts");
        var cache = new AccountCache(states, manager);
        manager.getCache("accounts").put("QQ:v1", "old");
        assertThat(cache.read("accounts", "A", "", () -> "fresh")).isEqualTo("fresh");
        assertThat(manager.getCache("accounts").get("QQ:v1").get()).isEqualTo("old");
    }
    @Test void generationChangeInvalidatesEveryLimitButPreservesUnrelatedAccount() {
        var states = mock(CacheStateReader.class);
        var manager = new ConcurrentMapCacheManager("accountLedgerEntries");
        var cache = new AccountCache(states, manager);
        when(states.read("A")).thenReturn(new CacheStateReader.State(1, false));
        when(states.read("B")).thenReturn(new CacheStateReader.State(0, false));
        assertThat(cache.read("accountLedgerEntries", "B", ":50", () -> "unrelated")).isEqualTo("unrelated");
        for (String limit : new String[]{":1", ":50", ":100"}) {
            manager.getCache("accountLedgerEntries").put("QQ:v0" + limit, "old");
            assertThat(cache.read("accountLedgerEntries", "A", limit, () -> "fresh")).isEqualTo("fresh");
        }
        assertThat(cache.<String>read("accountLedgerEntries", "B", ":50", () -> { throw new AssertionError("Unrelated cache was evicted"); })).isEqualTo("unrelated");
    }

    @Test void delayedReaderCannotRepopulateCurrentGenerationAfterCommit() throws Exception {
        var states = mock(CacheStateReader.class);
        when(states.read("A")).thenReturn(new CacheStateReader.State(0, false), new CacheStateReader.State(1, false));
        var manager = new ConcurrentMapCacheManager("accounts");
        var cache = new AccountCache(states, manager);
        var reading = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> cache.read("accounts", "A", "", () -> {
                reading.countDown();
                try { if (!commit.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
                return "old";
            }));
            assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
            commit.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("old");
            assertThat(manager.getCache("accounts").get("QQ:v0")).isNull();
            assertThat(manager.getCache("accounts").get("QQ:v1")).isNull();
        } finally { commit.countDown(); pool.shutdownNow(); }
    }
}
