
package com.flash.sale.api.service;

import java.util.List;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flash.sale.api.entity.Product;
import com.flash.sale.api.exception.OutOfStockException;
import com.flash.sale.api.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    private static final String CACHE_KEY_PREFIX = "product_stock_";

    public ProductService(
            ProductRepository productRepository,
            RedissonClient redissonClient) {
        this.productRepository = productRepository;
        this.redissonClient = redissonClient;
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public Product createProduct(Product product) {
        Product savedProduct = productRepository.save(product);

        RAtomicLong stock = redissonClient.getAtomicLong(
                CACHE_KEY_PREFIX + savedProduct.getId());

        stock.set(savedProduct.getStock());

        return savedProduct;
    }

    private boolean reserveStock(Long productId) {

        String luaScript = """
                local stock = redis.call('GET', KEYS[1])

                if not stock or tonumber(stock) == nil then
                    return 0
                end

                if tonumber(stock) > 0 then
                    redis.call('DECR', KEYS[1])
                    return 1
                end

                return 0
                """;

        Long result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                luaScript,
                RScript.ReturnType.INTEGER,
                List.of(CACHE_KEY_PREFIX + productId));

        return Long.valueOf(1).equals(result);
    }

    
@Transactional
public Product buyProduct(Long id) {

    // 1. Verify that the product exists.
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));

    // 2. Reserve one unit atomically in Redis.
    boolean reserved = reserveStock(id);

    if (!reserved) {
    throw new OutOfStockException("Product out of stock");
}

    // 3. Atomically decrement PostgreSQL stock.
    int rowsUpdated = productRepository.decrementStock(id);

    if (rowsUpdated == 0) {
    redissonClient.getAtomicLong(CACHE_KEY_PREFIX + id)
            .incrementAndGet();

    throw new OutOfStockException(
            "Database has no stock available; Redis reservation reversed");
}

    // 4. Return the latest database state.
    return productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));
}


    public String redisTest() {
        RAtomicLong stock = redissonClient.getAtomicLong("test_stock");

        stock.set(100);

        long before = stock.get();
        long after = stock.decrementAndGet();

        return "Before: " + before + ", After: " + after;
    }

    public String redisLuaTest() {

        String luaScript = """
                local stock = redis.call('GET', KEYS[1])

                if not stock or tonumber(stock) == nil then
                    return 0
                end

                if tonumber(stock) > 0 then
                    redis.call('DECR', KEYS[1])
                    return 1
                end

                return 0
                """;

        Long result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                luaScript,
                RScript.ReturnType.INTEGER,
                List.of("test_stock"));

        return "Result: " + result;
    }
}
