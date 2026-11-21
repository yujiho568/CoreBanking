package com.corebanking.openbanking.imported;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="datop_aggregate_datasets")
public class DatopAggregateDataset {
    @Id public String id;
    @Column(nullable=false) public String ownerId;
    @Column(nullable=false) public String sourceKind;
    @Column(nullable=false) public String sha256;
    @Column(nullable=false) public long rowCount;
    @Column(nullable=false) public Instant importedAt;
    protected DatopAggregateDataset() {}
}
