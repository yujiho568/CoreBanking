package com.corebanking.openbanking.imported;

import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.corebanking.openbanking.service.ExternalQueryCache;

@Service
public class DatopImportService {
    private final ImportedDatasetRepository datasets;
    private final JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManager entityManager;
    public DatopImportService(ImportedDatasetRepository datasets, JdbcTemplate jdbc, jakarta.persistence.EntityManager entityManager) {
        this.datasets=datasets; this.jdbc=jdbc; this.entityManager=entityManager;
    }

    /** Input mapping is explicit: never infer account IDs or debit/credit ledger entries. */
    @Transactional(rollbackFor=Exception.class)
    public ImportedDataset importFile(Path source, Path normalized, Path archive, String owner,
                                      String label, boolean synthetic) throws IOException {
        if (owner==null || owner.isBlank() || owner.length()>255 || label==null || label.isBlank() || label.length()>255)
            throw new IllegalArgumentException("Owner and source label are required (max 255 characters).");
        Files.createDirectories(archive);
        Path sourceSnapshot=snapshot(source,archive);
        Path normalizedSnapshot=snapshot(normalized,archive);
        String sourceHash=sourceSnapshot.getFileName().toString();
        String normalizedHash=normalizedSnapshot.getFileName().toString();
        String id=ExternalQueryCache.key(owner,sourceHash,normalizedHash,label,synthetic);
        if (datasets.existsById(id)) return datasets.findById(id).orElseThrow();
        ImportedDataset dataset=new ImportedDataset();
        dataset.id=id; dataset.ownerId=owner; dataset.sourceSha256=sourceHash;
        dataset.normalizedSha256=normalizedHash; dataset.sourceLabel=label;
        dataset.synthetic=synthetic; dataset.importedAt=Instant.now();
        // Persist, rather than merge, so a concurrent identical import cannot append duplicate rows.
        entityManager.persist(dataset);
        entityManager.flush();
        try (Reader reader=Files.newBufferedReader(normalizedSnapshot,StandardCharsets.UTF_8);
             CSVParser csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            if (!csv.getHeaderNames().equals(List.of("account_ref","occurred_at","amount","direction","description")))
                throw new IllegalArgumentException("Unexpected normalized CSV header.");
            List<Object[]> batch=new ArrayList<>();
            for (CSVRecord row:csv) {
                if (row.size()!=5) throw new IllegalArgumentException("Invalid column count at row "+row.getRecordNumber());
                String account=row.get("account_ref"), direction=row.get("direction"), description=row.get("description");
                BigDecimal amount=new BigDecimal(row.get("amount"));
                if (account.isBlank() || account.length()>128 || !List.of("I","O").contains(direction)
                        || amount.signum()<0 || amount.scale()>2 || amount.precision()-amount.scale()>17 || description.length()>500)
                    throw new IllegalArgumentException("Invalid normalized transaction at row "+row.getRecordNumber());
                LocalDateTime occurred=LocalDateTime.parse(row.get("occurred_at"));
                batch.add(new Object[]{id,account,occurred,amount,direction,description});
                dataset.rowCount++;
                if (batch.size()==500) { insert(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) insert(batch);
        }
        return dataset;
    }
    private void insert(List<Object[]> batch) {
        jdbc.batchUpdate("insert into imported_transactions (dataset_id,account_ref,occurred_at,amount,direction,description) values (?,?,?,?,?,?)",batch);
    }
    private Path snapshot(Path file, Path archive) throws IOException {
        Path temporary=Files.createTempFile(archive,"import-",".tmp");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try (InputStream input=new DigestInputStream(Files.newInputStream(file),digest)) {
                Files.copy(input,temporary,StandardCopyOption.REPLACE_EXISTING);
            }
            Path target=archive.resolve(HexFormat.of().formatHex(digest.digest()));
            try { Files.move(temporary,target); }
            catch (FileAlreadyExistsException exists) {
                if (Files.mismatch(temporary,target)!=-1) throw new IOException("Archive integrity failure",exists);
            }
            return target;
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        finally { Files.deleteIfExists(temporary); }
    }
}
