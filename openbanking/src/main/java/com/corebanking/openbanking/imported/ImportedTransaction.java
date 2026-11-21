package com.corebanking.openbanking.imported;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Table(name="imported_transactions", indexes=@Index(name="ix_imported_dataset_account_time_id", columnList="datasetId,accountRef,occurredAt,id"))
public class ImportedTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String datasetId;
    @Column(nullable=false, length=128) public String accountRef;
    @Column(nullable=false) public LocalDateTime occurredAt;
    @Column(nullable=false, precision=19, scale=2) public BigDecimal amount;
    @Column(nullable=false, length=1) public String direction;
    @Column(length=500) public String description;
    protected ImportedTransaction() {}
}
