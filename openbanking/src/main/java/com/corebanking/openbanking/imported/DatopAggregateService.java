package com.corebanking.openbanking.imported;
import com.corebanking.openbanking.service.ExternalQueryCache;
import jakarta.persistence.EntityManager;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service
public class DatopAggregateService {
    public static final String SOURCE="https://www.datop.or.kr/data/set/dataSetDetail.do?dataSetId=DSET000004";
    private static final List<String> EN=List.of("BASE_YM","HNDE_BANK_RPTV_CODE","OPENBANK_RPTV_CODE","FND_TPCD","TRAN_AMT");
    private static final List<String> KO=List.of("기준연월","(비식별)취급은행대표코드","(비식별)개설은행대표코드","자금구분코드","거래금액");
    private final DatopAggregateDatasetRepository datasets;
    private final DatopAggregateRepository rows;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    public DatopAggregateService(DatopAggregateDatasetRepository datasets,DatopAggregateRepository rows,EntityManager entityManager,JdbcTemplate jdbc) {
        this.datasets=datasets;this.rows=rows;this.entityManager=entityManager;this.jdbc=jdbc;
    }
    public record Dataset(String id,String providerDatasetId,String sourceUrl,String sourceKind,String sha256,long rowCount,Instant importedAt) {}
    public record Row(long id,String baseYm,String handlingBankCode,String openingBankCode,String fundTypeCode,String transactionAmount) {}
    public record Page(List<Row> rows,boolean hasMore,Long nextId) {}
    private Dataset view(DatopAggregateDataset d){return new Dataset(d.id,"DSET000004",SOURCE,d.sourceKind,d.sha256,d.rowCount,d.importedAt);}
    public List<Dataset> list(String owner){requireOwner(owner);return datasets.findAllByOwnerIdOrderByImportedAtDesc(owner).stream().map(this::view).toList();}
    private void requireOwner(String owner){if(owner==null || owner.isBlank() || owner.length()>255)throw new IllegalArgumentException("Invalid owner");}
    private void month(String month){
        try{if(month==null || !month.matches("[0-9]{6}"))throw new IllegalArgumentException("Month must be yyyyMM");YearMonth.parse(month,DateTimeFormatter.ofPattern("uuuuMM"));}
        catch(java.time.DateTimeException invalid){throw new IllegalArgumentException("Invalid yyyyMM month",invalid);}
    }
    @Transactional(rollbackFor=Exception.class)
    public Dataset importFile(Path file,Path archive,String owner,String kind,String encoding) throws IOException {
        requireOwner(owner);
        if(!List.of("PUBLIC_PREVIEW","DOWNLOADED_FILE").contains(kind))throw new IllegalArgumentException("Specify PUBLIC_PREVIEW or DOWNLOADED_FILE");
        Files.createDirectories(archive);
        Path temporary=Files.createTempFile(archive,"datop-",".tmp");String hash;Path stored;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=new DigestInputStream(Files.newInputStream(file),digest)){Files.copy(input,temporary,StandardCopyOption.REPLACE_EXISTING);}
            hash=HexFormat.of().formatHex(digest.digest());stored=archive.resolve(hash);
            try{Files.move(temporary,stored);}catch(FileAlreadyExistsException exists){if(Files.mismatch(temporary,stored)!=-1)throw new IOException("Archive integrity failure");}
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}finally{Files.deleteIfExists(temporary);}
        String id=ExternalQueryCache.key("DSET000004",owner,kind,hash);
        var existing=datasets.findById(id);if(existing.isPresent())return view(existing.get());
        DatopAggregateDataset d=new DatopAggregateDataset();d.id=id;d.ownerId=owner;d.sourceKind=kind;d.sha256=hash;d.importedAt=Instant.now();
        entityManager.persist(d);entityManager.flush();
        try(PushbackReader reader=new PushbackReader(Files.newBufferedReader(stored,java.nio.charset.Charset.forName(encoding)),1)) {
            int first=reader.read();if(first!=-1 && first!=0xFEFF)reader.unread(first);
            try(CSVParser csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
                if(!csv.getHeaderNames().equals(EN) && !csv.getHeaderNames().equals(KO))throw new IllegalArgumentException("Unexpected DSET000004 CSV headers");
                List<Object[]> batch=new ArrayList<>();
                for(CSVRecord row:csv) {
                    if(row.size()!=5)throw new IllegalArgumentException("Invalid column count at row "+row.getRecordNumber());
                    String ym=row.get(0),handling=row.get(1),opening=row.get(2),fund=row.get(3),amount=row.get(4);
                    month(ym);
                    if(!handling.matches("[A-Za-z0-9]{3}") || !opening.matches("[A-Za-z0-9]{3}") || !fund.matches("[A-Za-z0-9]{2}") || !amount.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)") )
                        throw new IllegalArgumentException("Invalid aggregate at row "+row.getRecordNumber());
                    BigDecimal value=new BigDecimal(amount.replace(",",""));if(value.precision()>23)throw new IllegalArgumentException("Amount exceeds NUMBER(23)");
                    batch.add(new Object[]{id,ym,handling,opening,fund,value});d.rowCount++;
                    if(batch.size()==500){insert(batch);batch.clear();}
                }
                if(!batch.isEmpty())insert(batch);
            }
        }
        return view(d);
    }
    private void insert(List<Object[]> batch){jdbc.batchUpdate("insert into datop_transfer_aggregates(dataset_id,base_ym,handling_bank_code,opening_bank_code,fund_type_code,transaction_amount) values (?,?,?,?,?,?)",batch);}
    public Page search(String owner,String dataset,String fromMonth,String toMonth,String bank,long afterId,int limit) {
        requireOwner(owner);month(fromMonth);month(toMonth);
        if(fromMonth.compareTo(toMonth)>0 || afterId<0 || limit<1 || limit>500)throw new IllegalArgumentException("Invalid search parameters");
        DatopAggregateDataset d=datasets.findById(dataset).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!d.ownerId.equals(owner))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var found=rows.search(dataset,fromMonth,toMonth,bank,afterId,PageRequest.of(0,limit+1));
        var result=found.stream().limit(limit).map(r->new Row(r.id,r.baseYm,r.handlingBankCode,r.openingBankCode,r.fundTypeCode,r.transactionAmount.toPlainString())).toList();
        return new Page(result,found.size()>limit,result.isEmpty()?null:result.get(result.size()-1).id());
    }
}
