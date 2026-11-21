package com.corebanking.openbanking.imported;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import com.corebanking.openbanking.service.ExternalQueryCache;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=DatopImportIntegrationTest.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:datop;DB_CLOSE_DELAY=-1;MODE=MySQL", "spring.jpa.hibernate.ddl-auto=create-drop"})
class DatopImportIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan(basePackageClasses=ImportedDataset.class)
    @EnableJpaRepositories(basePackageClasses=ImportedDatasetRepository.class)
    @Import({DatopImportService.class,ImportedQueryService.class})
    static class Config {
        @Bean ExternalQueryCache cache() { return new ExternalQueryCache(new ConcurrentMapCacheManager(),16); }
    }
    @Autowired DatopImportService importer;
    @Autowired ImportedQueryService query;
    @Autowired ImportedDatasetRepository datasets;
    @Autowired ImportedTransactionRepository rows;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;
    private static final String HEADER="account_ref,occurred_at,amount,direction,description\n";
    @BeforeEach void clean() { rows.deleteAll();datasets.deleteAll(); }
    @Test void importsArchivesDeduplicatesAndPagesEqualTimestamps() throws Exception {
        Path original=temp.resolve("source.bin"), csv=temp.resolve("normalized.csv"), archive=temp.resolve("archive");
        Files.writeString(original,"original-source-bytes");
        Files.writeString(csv,HEADER+"acct,2026-11-01T10:00:00,10.50,I,first\nacct,2026-11-01T10:00:00,20,O,second\nacct,2026-11-02T10:00:00,30,I,third\n");
        ImportedDataset dataset=importer.importFile(original,csv,archive,"owner","fixture",true);
        assertThat(dataset.rowCount).isEqualTo(3);
        assertThat(dataset.synthetic).isTrue();
        assertThat(Files.readString(archive.resolve(dataset.sourceSha256))).isEqualTo("original-source-bytes");
        assertThat(importer.importFile(original,csv,archive,"owner","fixture",true).id).isEqualTo(dataset.id);
        assertThat(rows.count()).isEqualTo(3);
        var from=LocalDateTime.parse("2026-11-01T00:00:00");
        var until=from.plusDays(1);
        var first=query.search("owner",dataset.id,"acct",from,until,null,0,1,false);
        assertThat(first.hasMore()).isTrue();
        var second=query.search("owner",dataset.id,"acct",from,until,first.nextTime(),first.nextId(),1,false);
        assertThat(second.rows()).hasSize(1);
        assertThat(second.rows().get(0).description()).isEqualTo("second");
        assertThat(second.hasMore()).isFalse();
        assertThat(query.search("owner",dataset.id,"acct",from,until,null,0,1,false).fetchedAt()).isEqualTo(first.fetchedAt());
        assertThatThrownBy(()->query.search("intruder",dataset.id,"acct",from,until,null,0,1,false))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes where lower(index_name)='ix_imported_dataset_account_time_id'",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentIdenticalImportsCannotAppendDuplicateRows() throws Exception {
        Path csv=temp.resolve("concurrent.csv");
        Files.writeString(csv,HEADER+"acct,2026-11-01T10:00:00,1,I,ok\n");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<ImportedDataset>> futures=new java.util.ArrayList<>();
        try {
            for(int i=0;i<2;i++) futures.add(pool.submit(()-> {
                ready.countDown();start.await();
                return importer.importFile(csv,csv,temp.resolve("archive"),"owner","same",false);
            }));
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            int successes=0;
            for(var future:futures) {
                try { assertThat(future.get(10,java.util.concurrent.TimeUnit.SECONDS).rowCount).isEqualTo(1);successes++; }
                catch(java.util.concurrent.ExecutionException collision) {
                    assertThat(collision.getCause()).isInstanceOfAny(org.springframework.dao.DataIntegrityViolationException.class,
                            jakarta.persistence.PersistenceException.class);
                }
            }
            assertThat(successes).isGreaterThanOrEqualTo(1);
            assertThat(datasets.count()).isEqualTo(1);
            assertThat(rows.count()).isEqualTo(1);
        } finally { start.countDown();pool.shutdownNow(); }
    }
    @Test void invalidRowRollsBackPreviouslyFlushedBatches() throws Exception {
        Path csv=temp.resolve("bad.csv");
        StringBuilder content=new StringBuilder(HEADER);
        for(int i=0;i<501;i++) content.append("acct,2026-11-01T10:00:00,1,I,ok\n");
        content.append("acct,2026-11-01T10:00:00,-1,I,bad\n");
        Files.writeString(csv,content);
        assertThatThrownBy(()->importer.importFile(csv,csv,temp.resolve("archive"),"owner","invalid",false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(rows.count()).isZero();
        assertThat(datasets.count()).isZero();
    }
}
