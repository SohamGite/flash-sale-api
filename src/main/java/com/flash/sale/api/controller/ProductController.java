
package com.flash.sale.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flash.sale.api.entity.Product;
import com.flash.sale.api.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    @GetMapping("/redis-test")
    public String redisTest() {
        return productService.redisTest();
    }

    @PostMapping("/redis-lua-test")
    public String redisLuaTest() {
        return productService.redisLuaTest();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping("/{id}/buy")
    public Product buyProduct(@PathVariable Long id) {
        return productService.buyProduct(id);
    }
}
