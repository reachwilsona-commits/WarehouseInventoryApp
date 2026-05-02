package com.company.inventory.repository;

import com.company.inventory.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class for Product Repository
 */
public interface ProductRepository extends JpaRepository<Product, String> {
}
