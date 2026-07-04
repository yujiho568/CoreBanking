package com.corebanking.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SeedBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedBatchConfig.class);
    private static final int BATCH_SIZE = 1_000;

    @Bean
    public Job seedJob(
            JobRepository jobRepository,
            Step seedAccountsStep,
            Step seedTransfersStep,
            Step seedLedgerEntriesStep
    ) {
        return new JobBuilder("seedJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(seedAccountsStep)
                .next(seedTransfersStep)
                .next(seedLedgerEntriesStep)
                .build();
    }

    @Bean
    public Step seedAccountsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SeedWriter writer
    ) {
        return new StepBuilder("seedAccountsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    writer.seedAccounts();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step seedTransfersStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SeedWriter writer
    ) {
        return new StepBuilder("seedTransfersStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    writer.seedTransfers();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step seedLedgerEntriesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SeedWriter writer
    ) {
        return new StepBuilder("seedLedgerEntriesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    writer.seedLedgerEntries();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public SeedWriter seedWriter(
            JdbcTemplate jdbcTemplate,
            @Value("${core-banking.seed.account-count:1000}") int accountCount,
            @Value("${core-banking.seed.transfer-count:10000}") int transferCount
    ) {
        return new SeedWriter(jdbcTemplate, accountCount, transferCount);
    }

    static class SeedWriter {

        private final JdbcTemplate jdbcTemplate;
        private final int accountCount;
        private final int transferCount;

        SeedWriter(JdbcTemplate jdbcTemplate, int accountCount, int transferCount) {
            this.jdbcTemplate = jdbcTemplate;
            this.accountCount = accountCount;
            this.transferCount = transferCount;
        }

        void seedAccounts() {
            log.info("Phase B account seed started. accounts={}", accountCount);

            String sql = """
                    INSERT IGNORE INTO accounts
                    (account_id, owner_name, balance, reserved_balance, status, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            for (int start = 1; start <= accountCount; start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE - 1, accountCount);
                List<Object[]> rows = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    Timestamp now = Timestamp.from(Instant.now());
                    rows.add(new Object[]{
                            accountId(i),
                            "Phase B User " + i,
                            new BigDecimal("1000000.00"),
                            BigDecimal.ZERO,
                            "ACTIVE",
                            0L,
                            now,
                            now
                    });
                }
                jdbcTemplate.batchUpdate(sql, rows);
            }

            log.info("Phase B account seed finished.");
        }

        void seedTransfers() {
            log.info("Phase B transfer seed started. transfers={}", transferCount);

            String sql = """
                    INSERT IGNORE INTO transfers
                    (transfer_id, idempotency_key, from_account_id, to_account_id, amount, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            for (int start = 1; start <= transferCount; start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE - 1, transferCount);
                List<Object[]> rows = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    Timestamp now = Timestamp.from(Instant.now());
                    rows.add(new Object[]{
                            transferId(i),
                            idempotencyKey(i),
                            accountId(sourceAccountIndex(i)),
                            accountId(targetAccountIndex(i)),
                            transferAmount(i),
                            "COMPLETED",
                            now,
                            now
                    });
                }
                jdbcTemplate.batchUpdate(sql, rows);
            }

            log.info("Phase B transfer seed finished.");
        }

        void seedLedgerEntries() {
            log.info("Phase B ledger seed started. ledgerEntries={}", transferCount * 2);

            String sql = """
                    INSERT IGNORE INTO ledger_entries
                    (entry_id, transfer_id, entry_type, account_id, amount, balance_after, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;

            for (int start = 1; start <= transferCount; start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE - 1, transferCount);
                List<Object[]> rows = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    Timestamp now = Timestamp.from(Instant.now());
                    BigDecimal amount = transferAmount(i);
                    rows.add(new Object[]{
                            ledgerEntryId(i, "D"),
                            transferId(i),
                            "DEBIT",
                            accountId(sourceAccountIndex(i)),
                            amount,
                            new BigDecimal("1000000.00").subtract(amount),
                            now
                    });
                    rows.add(new Object[]{
                            ledgerEntryId(i, "C"),
                            transferId(i),
                            "CREDIT",
                            accountId(targetAccountIndex(i)),
                            amount,
                            new BigDecimal("1000000.00").add(amount),
                            now
                    });
                }
                jdbcTemplate.batchUpdate(sql, rows);
            }

            log.info("Phase B ledger seed finished.");
        }

        private String accountId(int index) {
            return "PHASEB-ACC-%06d".formatted(index);
        }

        private String transferId(int index) {
            return "PHASEB-TR-%08d".formatted(index);
        }

        private String idempotencyKey(int index) {
            return "phase-b-key-%08d".formatted(index);
        }

        private String ledgerEntryId(int transferIndex, String side) {
            return "PHASEB-LE-%s-%08d".formatted(side, transferIndex);
        }

        private int sourceAccountIndex(int transferIndex) {
            return ((transferIndex - 1) % accountCount) + 1;
        }

        private int targetAccountIndex(int transferIndex) {
            return (transferIndex % accountCount) + 1;
        }

        private BigDecimal transferAmount(int transferIndex) {
            return BigDecimal.valueOf((transferIndex % 10) + 1).multiply(new BigDecimal("1000.00"));
        }
    }
}
