package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    @PostConstruct
    public void printCodec() {
        System.out.println("Current codec: " + redissonClient.getConfig().getCodec().getClass());
    }
    private final ProductMapper productMapper;

    @Autowired
    private RedissonClient redissonClient;
    private static final Long HOT_PRODUCT_ID = 39600L;  // 你的热点商品ID
    private static final String HOT_PRODUCT_KEY_PREFIX = "hot_product:";

    @Override
    @Transactional
    public Product createProduct(Product product) {
        // 检查商品名称是否已存在
        if (productMapper.existsByName(product.getName())) {
            throw new RuntimeException("商品名称已存在: " + product.getName());
        }
//      模拟接口耗时超长，触发数据库连接池耗尽。
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        product.setCreatedTime(now);
        product.setUpdatedTime(now);

        // 验证库存不能为负数
        if (product.getStock() < 0) {
            throw new RuntimeException("库存不能为负数");
        }

        // 验证价格必须大于0
        if (product.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("价格必须大于0");
        }

        // 插入商品
        productMapper.insert(product);
        return product;
    }

    @Override
    public Optional<Product> getProductById(Long id) {
        return productMapper.findById(id);
    }

    @Override
    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }

    @Override
    @Transactional
    public boolean deductStock(Long productId, Integer quantity) {
        int affectedRows = productMapper.deductStock(productId, quantity);
        return affectedRows > 0;
    }

    @Override
    @Transactional
    public void increaseStock(Long productId, Integer quantity) {
        productMapper.increaseStock(productId, quantity);
    }

    @Override
    public Integer getProductStock(Long productId) {
        return productMapper.getStock(productId);
    }

    @Override
    public Product getProductDetail(Long id) {
        if (HOT_PRODUCT_ID.equals(id)) {
            String key = HOT_PRODUCT_KEY_PREFIX + id;
            RBucket<Product> bucket = redissonClient.getBucket(key);

            // 先从 Redis 读缓存
            Product product = bucket.get();
            if (product != null) {
                return product;
            }

            // 缓存没命中，从数据库查
            product = productMapper.findById(id).orElse(null);
            if (product != null) {
                System.out.println("*********插入缓存");
                // 写入 Redis，设置10分钟过期时间
                bucket.set(product, 10, TimeUnit.MINUTES);
            }
            return product;
        }
        return productMapper.findById(id).orElse(null);
    }
}
