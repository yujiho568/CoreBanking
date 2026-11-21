package com.corebanking.openbanking.imported;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="datop_transfer_aggregates",indexes=@Index(name="ix_datop_dataset_month_bank",columnList="datasetId,baseYm,handlingBankCode,id"))
public class DatopAggregate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String datasetId;
    @Column(nullable=false,length=6) public String baseYm;
    @Column(nullable=false,length=3) public String handlingBankCode;
    @Column(nullable=false,length=3) public String openingBankCode;
    @Column(nullable=false,length=2) public String fundTypeCode;
    @Column(nullable=false,precision=23,scale=0) public BigDecimal transactionAmount;
    protected DatopAggregate() {}
}
