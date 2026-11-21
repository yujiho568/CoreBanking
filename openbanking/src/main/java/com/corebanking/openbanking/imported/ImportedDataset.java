package com.corebanking.openbanking.imported;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="imported_datasets")
public class ImportedDataset {
    @Id public String id;
    @Column(nullable=false) public String ownerId;
    @Column(nullable=false) public String sourceSha256;
    @Column(nullable=false) public String normalizedSha256;
    @Column(nullable=false) public String sourceLabel;
    public boolean synthetic;
    public long rowCount;
    public Instant importedAt;
    protected ImportedDataset() {}
}
