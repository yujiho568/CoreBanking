package com.corebanking.openbanking.imported;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(classes=DatopImportIntegrationTest.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:datopaggregates;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(DatopAggregateService.class)
class DatopAggregateIntegrationTest {
    @Autowired DatopAggregateService service;
    @Autowired DatopAggregateRepository rows;
    @Autowired DatopAggregateDatasetRepository datasets;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;
    static final String HEADER="BASE_YM,HNDE_BANK_RPTV_CODE,OPENBANK_RPTV_CODE,FND_TPCD,TRAN_AMT\n";
    @BeforeEach void clean(){rows.deleteAll();datasets.deleteAll();}
    @Test void preservesOriginalFieldsPrecisionProvenanceAndDeduplicates()throws Exception{
        Path file=temp.resolve("aggregate.csv"),archive=temp.resolve("archive");
        String source="\uFEFF"+HEADER+"202107,AAA,BBB,00,12345678901234567890123\n202108,AAA,CCC,01,42\n";
        Files.writeString(file,source);
        var dataset=service.importFile(file,archive,"owner","PUBLIC_PREVIEW","UTF-8");
        assertThat(dataset.rowCount()).isEqualTo(2);assertThat(dataset.sourceKind()).isEqualTo("PUBLIC_PREVIEW");
        assertThat(Files.readString(archive.resolve(dataset.sha256()))).isEqualTo(source);
        assertThat(service.importFile(file,archive,"owner","PUBLIC_PREVIEW","UTF-8").id()).isEqualTo(dataset.id());
        assertThat(rows.count()).isEqualTo(2);
        var first=service.search("owner",dataset.id(),"202107","202108","AAA",0,1);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.rows().get(0).transactionAmount()).isEqualTo("12345678901234567890123");
        var next=service.search("owner",dataset.id(),"202107","202108","AAA",first.nextId(),1);
        assertThat(next.rows()).hasSize(1);assertThat(next.rows().get(0).baseYm()).isEqualTo("202108");
        assertThat(next.hasMore()).isFalse();
        assertThatThrownBy(()->service.search("intruder",dataset.id(),"202107","202108",null,0,100)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.search("owner",dataset.id(),"202113","202113",null,0,100)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes where lower(index_name)='ix_datop_dataset_month_bank'",Integer.class)).isEqualTo(1);
    }
    @Test void invalidRowRollsBackBatches()throws Exception{
        Path file=temp.resolve("invalid.csv");StringBuilder csv=new StringBuilder(HEADER);
        for(int i=0;i<501;i++)csv.append("202107,AAA,BBB,00,1\n");
        csv.append("202113,AAA,BBB,00,1\n");Files.writeString(file,csv);
        assertThatThrownBy(()->service.importFile(file,temp.resolve("archive"),"owner","DOWNLOADED_FILE","UTF-8")).isInstanceOf(IllegalArgumentException.class);
        assertThat(rows.count()).isZero();assertThat(datasets.count()).isZero();
    }
}
