package com.corebanking.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CacheInvalidationWorkerTest {
    @Test void deletionFailureRetainsPendingJobAndSuccessfulRetryCanComplete() {
        var repository = mock(CacheInvalidationRepository.class);
        var manager = mock(CacheManager.class);
        var accounts = mock(Cache.class);
        var ledger = mock(Cache.class);
        when(manager.getCache("accounts")).thenReturn(accounts);
        when(manager.getCache("accountLedgerEntries")).thenReturn(ledger);
        var job = new CacheInvalidation("A");
        when(repository.lock(job.getId())).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("Redis timeout")).when(accounts).evict("A");
        var worker = new CacheInvalidationWorker(repository, manager, mock(org.springframework.beans.factory.ObjectProvider.class));
        worker.process(job.getId());
        assertThat(job.isCompleted()).isFalse();
        assertThat(job.getFailures()).isEqualTo(1);
        // A distinct due job tests full deletion coverage without bypassing the backoff guard.
        var due = new CacheInvalidation("B");
        when(repository.lock(due.getId())).thenReturn(Optional.of(due));
        worker.process(due.getId());
        assertThat(due.isCompleted()).isTrue();
        verify(ledger).evict("B:1");
        verify(ledger).evict("B:50");
        verify(ledger).evict("B:100");
        verify(accounts, never()).evict("unrelated");
    }
}
