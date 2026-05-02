package com.company.inventory.repository;

import com.company.inventory.domain.entity.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Class for Inventory Repository
 */
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    /**
     * Acquire row-level write locks on the requested SKUs.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    List<Inventory> findBySkuInOrderBySkuAsc(Collection<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findBySku(String sku);
}
