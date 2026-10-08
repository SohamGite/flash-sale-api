package com.flash.sale.api.service;

import java.util.concurrent.TimeUnit;
import java.util.List;

import org.redisson.api.RBucket;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScript;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flash.sale.api.entity.Product;
import com.flash.sale.api.repository.ProductRepository;


@Service
public class ProductService {
    
    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    private static final String CACHE_KEY_PREFIX = "product_stock_";

    public ProductService(ProductRepository productRepository, RedissonClient redissonClient) {
        this.productRepository = productRepository;
        this.redissonClient = redissonClient;
    }

    public Product getProduct(Long id) {
        RBucket<Product> productBucket = redissonClient.getBucket(CACHE_KEY_PREFIX + id);
        Product cachedProduct = productBucket.get();
        
        if (cachedProduct != null) {
            return cachedProduct; 
        }

        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        productBucket.set(product);
        return product;
    }

    public Product createProduct(Product product) {
        Product savedProduct = productRepository.save(product);
        redissonClient.getBucket(CACHE_KEY_PREFIX + savedProduct.getId()).set(savedProduct);
        return savedProduct;
    }

    public String redisTest() {
        RAtomicLong stock =
            redissonClient.getAtomicLong("test_stock");

    stock.set(100);

    long before = stock.get();
    long after = stock.decrementAndGet();

    return "Before: " + before + ", After: " + after;
    }

    public String redisLuaTest() {

        RScript script = redissonClient.getScript();

        String luaScript = """
            local stock = redis.call('GET', KEYS[1])

            if tonumber(stock) > 0 then
                redis.call('DECR', KEYS[1])
                return 1
            end

            return 0
            """;

        Long result = script.eval(
            RScript.Mode.READ_WRITE,
            luaScript,
            RScript.ReturnType.INTEGER,
            List.of("test_stock")
        );

        return "Result: " + result;
    }

    @Transactional // Ensures DB rollback if anything fails after saving
    public Product buyProduct(Long id) {
        RBucket<Product> productBucket = redissonClient.getBucket(CACHE_KEY_PREFIX + id);
        Product currentProduct = productBucket.get();
        
        // --- FAST-FAIL OPTIMIZATION ---
        // If the cache knows it's out of stock, reject immediately. 
        // Do NOT waste server resources waiting for a lock.
        if (currentProduct != null && currentProduct.getStock() <= 0) {
            throw new RuntimeException("Product out of stock");
        }

        RLock lock = redissonClient.getLock("product_lock_" + id);
        
        try {
            // Try to acquire lock. Wait up to 5 seconds in queue. Hold lock for max 10 seconds.
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // --- BEGIN PROTECTED LOGIC ---
                    
                    // Re-fetch product inside the lock to get the most absolute, up-to-date state
                    Product product = getProduct(id); 
                    
                    if (product.getStock() <= 0) {
                        throw new RuntimeException("Product out of stock");
                    }
                    
                    // Decrement Inventory
                    product.setStock(product.getStock() - 1);
                    
                    // Update Database (Because of @Transactional, this is safe)
                    Product updatedProduct = productRepository.save(product);
                    
                    // Sync the Redis Cache with the new stock value
                    productBucket.set(updatedProduct);
                    
                    return updatedProduct;
                    
                    // --- END PROTECTED LOGIC ---
                } finally {
                    lock.unlock(); // Always release lock to prevent deadlocks
                }
            } else {
                throw new RuntimeException("Server is busy, too much traffic! Please try again.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transaction interrupted");
        }   
    }

}