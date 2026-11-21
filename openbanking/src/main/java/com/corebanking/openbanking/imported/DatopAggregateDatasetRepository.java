package com.corebanking.openbanking.imported;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DatopAggregateDatasetRepository extends JpaRepository<DatopAggregateDataset,String> {
    List<DatopAggregateDataset> findAllByOwnerIdOrderByImportedAtDesc(String ownerId);
}
