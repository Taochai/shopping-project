package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
    private static final Object NULL_CACHE_MARKER = new Object(); // 使用Object作为标记 解决缓存穿透问题（访问不存在的数据）
    // 布隆过滤器，用于判断商品是否存在  解决缓存穿透问题（访问不存在的数据）
    private RBloomFilter<Long> productBloomFilter;

    //    @PostConstruct
//    public void init() {
//        productBloomFilter = redissonClient.getBloomFilter("productBloomFilter");
//        // 初始化布隆过滤器，预计元素数量100000，误判率0.01%
//        productBloomFilter.tryInit(100000L, 0.0001);
//
//        // 预热：将现有商品ID加入布隆过滤器
//        List<Long> existingIds = productMapper.findAllIds();
//        for (Long id : existingIds) {
//            productBloomFilter.add(id);
//        }
//    }
    private final ProductMapper productMapper;

    @Autowired
    private RedissonClient redissonClient;
    private static final Long HOT_PRODUCT_ID = 39600L;  // 你的热点商品ID
    private static final String HOT_PRODUCT_KEY_PREFIX = "hot_product:";
    private static final String HOT_PRODUCT_KEY_LOCK_PREFIX = "hot_product_lock:";

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
//        // 使用布隆过滤器快速判断商品是否存在
//        if (!productBloomFilter.contains(id)) {
//            log.warn("商品ID不存在于布隆过滤器中，直接返回null，ID: {}", id);
//            return null;
//        }

        if (HOT_PRODUCT_ID.equals(id)) {
            String key = HOT_PRODUCT_KEY_PREFIX + id;
            RBucket<Object> bucket = redissonClient.getBucket(key);

            // 先从 Redis 读缓存
            Object product = bucket.get();
            if (product != null) {
                if (product == NULL_CACHE_MARKER) {
                    log.info("命中空值缓存，商品不存在，ID: {}", id);
                    return null;
                }
                log.info("从缓存读取商品数据，ID: {}", id);
                return (Product) product; // 类型转换
            }
            String productLockKey = HOT_PRODUCT_KEY_LOCK_PREFIX + id;
            RLock lock = redissonClient.getLock(productLockKey);
            try {
                if (lock.tryLock(1, 5, TimeUnit.SECONDS)) {
                    product = bucket.get();
                    if (product != null) {
                        if (product == NULL_CACHE_MARKER) {
                            return null;
                        }
                        return (Product) product;
                    }
                    log.info("重建缓存");
                    // 缓存没命中，从数据库查
                    product = productMapper.findById(id).orElse(null);
                    if (product != null) {
                        // 写入 Redis，设置10s过期时间
                        bucket.set(product, 10 + new Random().nextInt(5), TimeUnit.SECONDS);
                    } else {
                        // 7. 应对缓存穿透：缓存空值
                        bucket.set(NULL_CACHE_MARKER, 5, TimeUnit.MINUTES);
                        log.warn("数据库不存在该商品，缓存空值防止穿透，商品ID: {}", id);
                    }
                    return (Product) product;
                } else {
                    Thread.sleep(50);
                    product = bucket.get();
                    if (product != null) {
                        if (product == NULL_CACHE_MARKER) {
                            return null;
                        }
                        return (Product) product;
                    }
                    return productMapper.findById(id).orElse(null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取锁过程被中断，直接查询数据库，商品ID: {}", id);
                return productMapper.findById(id).orElse(null);
            } finally {
                lock.unlock();
            }
        }
        return productMapper.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public Product updateProductSelective(Long id, Product product) {
        log.info("开始选择性更新商品，ID: {}, 数据: {}", id, product);

        // 1. 检查商品是否存在
        Product existingProduct = getProductDetail(id);
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

        return getProductDetail(id);
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
