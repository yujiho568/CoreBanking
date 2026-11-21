package com.corebanking.openbanking.service;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalQueryCacheTest {
    @Test void hitRefreshAndFailureRecovery() {
        var cache=new ExternalQueryCache(new ConcurrentMapCacheManager(),2);
        AtomicInteger calls=new AtomicInteger();
        assertThat(cache.read("balances","a",false,calls::incrementAndGet)).isEqualTo(1);
        assertThat(cache.read("balances","a",false,calls::incrementAndGet)).isEqualTo(1);
        assertThat(cache.read("balances","a",true,calls::incrementAndGet)).isEqualTo(2);
        assertThat(cache.read("balances","b",false,calls::incrementAndGet)).isEqualTo(3);
        assertThatThrownBy(()->cache.read("balances","bad",false,()->{throw new IllegalStateException();}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cache.read("balances","bad",false,()->4)).isEqualTo(4);
    }
    @Test void redisFailureFallsBackWithoutRetryingProvider() {
        CacheManager manager=mock(CacheManager.class);
        Cache redis=mock(Cache.class);
        when(manager.getCache("balances")).thenReturn(redis);
        when(redis.get("key")).thenThrow(new IllegalStateException("offline"));
        doThrow(new IllegalStateException("offline")).when(redis).put("key",42);
        var cache=new ExternalQueryCache(manager,1);
        AtomicInteger calls=new AtomicInteger();
        assertThat(cache.read("balances","key",false,()->{calls.incrementAndGet();return 42;})).isEqualTo(42);
        assertThat(calls).hasValue(1);
    }
    @Test void concurrentIdenticalRequestsShareOneProviderCallAndDifferentKeysAreRejected() throws Exception {
        var cache=new ExternalQueryCache(new ConcurrentMapCacheManager(),1);
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            Future<Integer> first=pool.submit(()->cache.read("b","a",false,()-> {
                calls.incrementAndGet();entered.countDown();
                try { if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                return 7;
            }));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            List<Future<Integer>> followers=new ArrayList<>();
            for(int i=0;i<6;i++) followers.add(pool.submit(()->cache.read("b","a",false,()->{calls.incrementAndGet();return 7;})));
            assertThatThrownBy(()->cache.read("b","different",false,()->8)).isInstanceOf(ResponseStatusException.class);
            release.countDown();
            assertThat(first.get(5,TimeUnit.SECONDS)).isEqualTo(7);
            for(var follower:followers) assertThat(follower.get(5,TimeUnit.SECONDS)).isEqualTo(7);
            assertThat(calls).hasValue(1);
        } finally { release.countDown();pool.shutdownNow(); }
    }
    @Test void keySeparatesAllQueryDimensionsWithoutExposingIdentifiers() {
        assertThat(ExternalQueryCache.key("owner","account","2026-01-01",1))
            .isNotEqualTo(ExternalQueryCache.key("other","account","2026-01-01",1))
            .isNotEqualTo(ExternalQueryCache.key("owner","account","2026-01-01",2))
            .hasSize(64).doesNotContain("owner");
        assertThat(ExternalQueryCache.key("ab","c")).isNotEqualTo(ExternalQueryCache.key("a","bc"));
    }
}
