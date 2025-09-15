package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public Product createProduct(Product product) {
        // 检查商品名称是否已存在
        if (productMapper.existsByName(product.getName())) {
            throw new RuntimeException("商品名称已存在: " + product.getName());
        }

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
}
