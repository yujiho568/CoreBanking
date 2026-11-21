package com.corebanking.openbanking.imported;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("performance")
@SpringBootTest(classes=DatopImportIntegrationTest.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:datopbench;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
class DatopQueryBenchmark {
    @Autowired DatopImportService importer;
    @Autowired ImportedQueryService query;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;
    @Test void comparesScanIndexAndWarmCacheWithSyntheticRows() throws Exception {
        Path csv=temp.resolve("synthetic.csv");
        try(var writer=Files.newBufferedWriter(csv)) {
            writer.write("account_ref,occurred_at,amount,direction,description\n");
            for(int i=0;i<30000;i++) writer.write("acct-"+(i%300)+",2026-11-01T10:00:00,10,I,synthetic\n");
        }
        var dataset=importer.importFile(csv,csv,temp.resolve("archive"),"benchmark","synthetic fixture",true);
        assertThat(dataset.rowCount).isEqualTo(30000);
        var from=LocalDateTime.parse("2026-11-01T00:00:00");var until=from.plusDays(1);
        Runnable fresh=()->assertThat(query.search("benchmark",dataset.id,"acct-42",from,until,null,0,50,true).rows()).hasSize(50);
        jdbc.execute("drop index ix_imported_dataset_account_time_id");
        for(int i=0;i<5;i++) fresh.run();
        double[] scan=measure(30,fresh);
        jdbc.execute("create index ix_imported_dataset_account_time_id on imported_transactions(dataset_id,account_ref,occurred_at,id)");
        for(int i=0;i<5;i++) fresh.run();
        double[] indexed=measure(30,fresh);
        double[] cached=measure(30,()->assertThat(query.search("benchmark",dataset.id,"acct-42",from,until,null,0,50,false).rows()).hasSize(50));
        Path output=Path.of("build/reports/datop-query-benchmark.json");Files.createDirectories(output.getParent());
        Files.writeString(output,"{\n  \"environment\":\"H2 MySQL mode, synthetic 30000 rows / 300 accounts, ConcurrentMapCache (not Redis), 30 sequential samples per case\",\n"
            +"  \"scan\":"+summary(scan)+",\n  \"indexed\":"+summary(indexed)+",\n  \"warm_cache\":"+summary(cached)+"\n}\n");
    }
    static double[] measure(int n,Runnable action) {
        double[] samples=new double[n];
        for(int i=0;i<n;i++){long start=System.nanoTime();action.run();samples[i]=(System.nanoTime()-start)/1e6;}
        return samples;
    }
    static String summary(double[] values) {
        double sum=Arrays.stream(values).sum();Arrays.sort(values);
        return String.format(Locale.ROOT,"{\"mean_ms\":%.4f,\"p95_ms\":%.4f,\"throughput_per_second\":%.2f,\"errors\":0}",
            sum/values.length,values[(int)Math.ceil(values.length*.95)-1],values.length*1000/sum);
    }
}
