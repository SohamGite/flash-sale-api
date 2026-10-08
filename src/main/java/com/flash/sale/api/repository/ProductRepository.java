package com.flash.sale.api.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.flash.sale.api.entity.Product;

@Repository 
public interface ProductRepository extends JpaRepository<Product, Long> {
    
}
