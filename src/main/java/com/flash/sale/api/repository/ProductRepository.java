
package com.flash.sale.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.flash.sale.api.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying
    @Query("""
        UPDATE Product p
        SET p.stock = p.stock - 1
        WHERE p.id = :id AND p.stock > 0
        """)
    int decrementStock(@Param("id") Long id);
}
