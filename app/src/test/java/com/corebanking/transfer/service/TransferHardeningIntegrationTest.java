package com.corebanking.transfer.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.account.service.AccountReadService;
import com.corebanking.common.cache.*;
import com.corebanking.ledger.service.LedgerReadService;
import com.corebanking.ledger.repository.LedgerEntryRepository;
import com.corebanking.transfer.repository.TransferRepository;
import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.Transfer;
import com.corebanking.transfer.entity.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Dedicated MySQL 8 + Redis required. No test-wide rollback: real commits are essential.
@EnabledIfEnvironmentVariable(named = "COREBANKING_INTEGRATION", matches = "true")
@SpringBootTest(properties = {"spring.cache.type=redis", "core-banking.cache.worker-enabled=false", "core-banking.account.lock-mode=pessimistic"})
@AutoConfigureMockMvc
class TransferHardeningIntegrationTest {
    @Autowired TransferWriteService service;
    @Autowired TransferRepository transfers;
    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledger;
    @Autowired AccountReadService accountReads;
    @Autowired LedgerReadService ledgerReads;
    @Autowired CacheInvalidationRepository jobs;
    @Autowired CacheInvalidationWorker worker;
    @Autowired CacheStateReader states;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired org.springframework.cache.CacheManager caches;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    String a, b;
    @BeforeEach void setup() {
        a = "T" + UUID.randomUUID(); b = "T" + UUID.randomUUID();
        accounts.saveAllAndFlush(List.of(Account.open(a, "test", new BigDecimal("1000")), Account.open(b, "test", new BigDecimal("1000"))));
    }
    CreateTransferRequest request(String key, String from, String to, String amount) {
        return new CreateTransferRequest(key, from, to, new BigDecimal(amount));
    }
    <T> List<T> concurrent(int n, IntFunction<T> action) throws Exception {
        var barrier = new CyclicBarrier(n);
        var pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) { int index = i; futures.add(pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return action.apply(index); })); }
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }
    void verifyTransfers(List<Transfer> results) {
        assertThat(accounts.findById(a).orElseThrow().getBalance().add(accounts.findById(b).orElseThrow().getBalance())).isEqualByComparingTo("2000");
        for (String id : List.of(a, b)) {
            assertThat(accounts.findById(id).orElseThrow().getReservedBalance()).isZero();
            assertThat(accounts.findById(id).orElseThrow().getBalance()).isNotNegative();
        }
        for (var transfer : results) {
            assertThat(transfers.findById(transfer.getTransferId()).orElseThrow().getStatus()).isEqualTo(TransferStatus.COMPLETED);
            var entries = ledger.findByTransferIdOrderByCreatedAtAsc(transfer.getTransferId());
            assertThat(entries).hasSize(2);
            assertThat(entries).extracting(e -> e.getAccountId()).containsExactlyInAnyOrder(a, b);
        }
    }
    @Test void opposingDirectionsAndHotSourcePreserveMoneyAndLedger() throws Exception {
        verifyTransfers(concurrent(8, i -> service.execute(request(UUID.randomUUID().toString(), i % 2 == 0 ? a : b, i % 2 == 0 ? b : a, "10"))));
        verifyTransfers(concurrent(8, i -> service.execute(request(UUID.randomUUID().toString(), a, b, "10"))));
        assertThat(accounts.findById(a).orElseThrow().getBalance()).isEqualByComparingTo("920");
    }
    @Test void uniqueConstraintIsActuallyEnforcedAndConcurrentReplayHasOneEffect() throws Exception {
        String key = UUID.randomUUID().toString();
        var results = concurrent(8, i -> service.execute(request(key, a, b, i % 2 == 0 ? "10.0" : "10.00")));
        assertThat(results).extracting(Transfer::getTransferId).containsOnly(results.get(0).getTransferId());
        verifyTransfers(results);
        assertThat(accounts.findById(a).orElseThrow().getBalance()).isEqualByComparingTo("990");
        assertThatThrownBy(() -> transfers.saveAndFlush(Transfer.requested(UUID.randomUUID().toString(), key, a, b, BigDecimal.TEN)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void httpReplayAndConflictsExposeRealStatusAndBody() throws Exception {
        String key = UUID.randomUUID().toString();
        var original = request(key, a, b, "10");
        var first = http.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(original))).andReturn().getResponse();
        var replay = http.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(original))).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(json.readTree(replay.getContentAsString()).get("transferId")).isEqualTo(json.readTree(first.getContentAsString()).get("transferId"));
        var responses = concurrent(4, i -> {
            try { return http.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(key, a, b, "11")))).andReturn().getResponse(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        for (var response : responses) {
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(json.readTree(response.getContentAsString()).get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        }
        assertThat(accounts.findById(a).orElseThrow().getBalance()).isEqualByComparingTo("990");
    }
    @Test void concurrentHttpSameKeyReturnsOneResultAndDifferentPayloadConflicts() throws Exception {
        for (boolean different : new boolean[]{false, true}) {
            String key = UUID.randomUUID().toString();
            var responses = concurrent(2, i -> {
                try { return http.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request(key, a, b, different && i == 1 ? "11" : "10"))))
                    .andReturn().getResponse(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            var stored = transfers.findByIdempotencyKey(key).orElseThrow();
            assertThat(ledger.findByTransferIdOrderByCreatedAtAsc(stored.getTransferId())).hasSize(2);
            if (different) assertThat(responses).extracting(r -> r.getStatus()).containsExactlyInAnyOrder(201, 409);
            else {
                assertThat(responses).extracting(r -> r.getStatus()).containsOnly(201);
                for (var response : responses) assertThat(json.readTree(response.getContentAsString()).get("transferId").asText()).isEqualTo(stored.getTransferId());
            }
        }
    }

    @Test void insufficientFundsRollBackTransferAccountsLedgerAndOutbox() {
        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.execute(request(key, a, b, "1001"))).isInstanceOf(com.corebanking.account.exception.InsufficientBalanceException.class);
        assertThat(transfers.findByIdempotencyKey(key)).isEmpty();
        assertThat(accounts.findById(a).orElseThrow().getBalance()).isEqualByComparingTo("1000");
        assertThat(accounts.findById(a).orElseThrow().getReservedBalance()).isZero();
        assertThat(states.read(a)).isEqualTo(new CacheStateReader.State(0, false));
        assertThat(ledgerReads.getAccountLedgerEntries(a, 100)).isEmpty();
    }
    @Test void deletionThenDatabaseRollbackLeavesJobAvailableForSafeReprocessing() {
        service.execute(request(UUID.randomUUID().toString(), a, b, "10"));
        var job = jobs.findAll().stream().filter(j -> j.getAccountId().equals(a)).findFirst().orElseThrow();
        caches.getCache("accounts").put(a, "obsolete legacy key");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var locked = jobs.lock(job.getId()).orElseThrow();
            caches.getCache("accounts").evict(a);
            locked.complete();
            jobs.flush();
            throw new IllegalStateException("simulate completion transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(caches.getCache("accounts").get(a)).isNull();
        assertThat(jobs.findById(job.getId()).orElseThrow().isCompleted()).isFalse();
        worker.process(job.getId());
        assertThat(jobs.findById(job.getId()).orElseThrow().isCompleted()).isTrue();
    }

    @Test void pendingWorkBypassesBothCachesAndLimitsThenConcurrentWorkersCompleteSafely() throws Exception {
        accountReads.getAccountResponse(a); accountReads.getAccountResponse(b);
        for (String id : List.of(a, b)) for (int limit : new int[]{1, 50, 100}) ledgerReads.getAccountLedgerEntryResponses(id, limit);
        service.execute(request(UUID.randomUUID().toString(), a, b, "10"));
        service.execute(request(UUID.randomUUID().toString(), a, b, "10"));
        assertThat(states.read(a)).isEqualTo(new CacheStateReader.State(2, true));
        assertThat(accountReads.getAccountResponse(a).balance()).isEqualByComparingTo("980");
        assertThat(accountReads.getAccountResponse(b).balance()).isEqualByComparingTo("1020");
        for (String id : List.of(a, b)) {
            assertThat(ledgerReads.getAccountLedgerEntryResponses(id, 1)).hasSize(1);
            assertThat(ledgerReads.getAccountLedgerEntryResponses(id, 50)).hasSize(2);
            assertThat(ledgerReads.getAccountLedgerEntryResponses(id, 100)).hasSize(2);
        }
        var own = jobs.findAll().stream().filter(j -> j.getAccountId().equals(a) || j.getAccountId().equals(b)).toList();
        var firstA = own.stream().filter(j -> j.getAccountId().equals(a)).findFirst().orElseThrow();
        worker.process(firstA.getId());
        assertThat(states.read(a)).isEqualTo(new CacheStateReader.State(2, true));
        for (var job : own) concurrent(2, i -> { worker.process(job.getId()); return true; });
        assertThat(states.read(a)).isEqualTo(new CacheStateReader.State(2, false));
        assertThat(accountReads.getAccountResponse(a).balance()).isEqualByComparingTo("980");
    }
}
