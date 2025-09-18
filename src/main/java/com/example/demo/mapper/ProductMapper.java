package com.example.demo.mapper;

import com.example.demo.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductMapper {

    @Insert("INSERT INTO products (name, price, stock, description, created_time, updated_time) " +
            "VALUES (#{name}, #{price}, #{stock}, #{description}, #{createdTime}, #{updatedTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);
    @Select("SELECT * FROM products WHERE id = #{id}")
    Optional<Product> findById(Long id);

    @Select("SELECT * FROM products")
    List<Product> findAll();

    @Update("UPDATE products SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("UPDATE products SET stock = stock + #{quantity} WHERE id = #{productId}")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Select("SELECT stock FROM products WHERE id = #{productId}")
    Integer getStock(Long productId);

    @Select("SELECT COUNT(*) FROM products WHERE name = #{name}")
    boolean existsByName(String name);

    // 选择性更新商品（只更新非空字段）
    @Update("<script>" +
            "UPDATE products " +
            "<set>" +
            "  <if test='name != null'>name = #{name},</if>" +
            "  <if test='price != null'>price = #{price},</if>" +
            "  <if test='stock != null'>stock = #{stock},</if>" +
            "  <if test='description != null'>description = #{description},</if>" +
            "  updated_time = #{updatedTime}" +
            "</set>" +
            "WHERE id = #{id}" +
            "</script>")
    int updateSelectiveById(Product product);

    @Select("SELECT COUNT(*) FROM products WHERE name = #{name} AND id != #{id}")
    boolean existsByNameExcludeId(@Param("name") String name, @Param("id") Long id);
}
