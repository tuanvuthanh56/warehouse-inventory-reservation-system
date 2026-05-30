package com.example.inventory.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.sku in :skus order by i.sku")
    List<InventoryEntity> findAllBySkuInForUpdate(@Param("skus") Collection<String> skus);
}
