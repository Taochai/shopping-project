package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
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
    public Product getProductById(Long id) {
        return productMapper.findById(id).orElse(null);
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

            //模拟接口耗时超长，触发数据库连接池耗尽。
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.debug("缓存没命中，从数据库查");
            // 缓存没命中，从数据库查
            product = productMapper.findById(id).orElse(null);

            if (product != null) {
                System.out.println("*********插入缓存");
                // 写入 Redis，设置10分钟过期时间
                bucket.set(product, 10, TimeUnit.SECONDS);
            }
            return product;
        }
        return productMapper.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public Product updateProductSelective(Long id, Product product) {
        log.info("开始选择性更新商品，ID: {}, 数据: {}", id, product);

        // 1. 检查商品是否存在
        Product existingProduct = getProductById(id);
        if (existingProduct == null) {
            throw new RuntimeException("商品不存在: " + product.getName());
        }
        // 2. 如果提供了名称且与原来不同，检查名称是否已存在
        if (product.getName() != null && !existingProduct.getName().equals(product.getName())) {
            if (productMapper.existsByNameExcludeId(product.getName(), id)) {
                throw new RuntimeException("商品名称已存在: " + product.getName());
            }
        }

        // 3. 验证库存
        if (product.getStock() != null && product.getStock() < 0) {
            throw new RuntimeException("库存不能为负数");
        }

        // 4. 验证价格
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("价格必须大于0");
        }

        // 5. 设置更新信息
        product.setId(id);
        product.setUpdatedTime(LocalDateTime.now());

        // 6. 执行选择性更新
        int affectedRows = productMapper.updateSelectiveById(product);
        if (affectedRows == 0) {
            throw new RuntimeException("更新商品失败，可能商品不存在");
        }

        log.info("商品选择性更新成功，影响行数: {}", affectedRows);

        // 7. 清除缓存
        clearProductCache(id);

        return getProductById(id);
    }

    // 清除商品缓存
    private void clearProductCache(Long productId) {
        try {
            String cacheKey = HOT_PRODUCT_KEY_PREFIX + productId;
            redissonClient.getBucket(cacheKey).delete();
            log.debug("已清除商品缓存: {}", productId);
        } catch (Exception e) {
            log.warn("清除商品缓存失败: {}", e.getMessage());
        }
    }

}
