package com.example.demo.service;



import com.example.demo.entity.Product;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductService {
    @Transactional
    Product createProduct(Product product);

    Product getProductById(Long id);
    List<Product> getAllProducts();
    boolean deductStock(Long productId, Integer quantity);
    void increaseStock(Long productId, Integer quantity);
    Integer getProductStock(Long productId);

    Product getProductDetail(Long id);

    Product updateProductSelective(Long id, Product product);

}