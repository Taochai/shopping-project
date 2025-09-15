package com.example.demo.mapper;


import com.example.demo.entity.OrderItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("INSERT INTO order_items (order_id, product_id, quantity, price, subtotal) " +
            "VALUES (#{orderId}, #{productId}, #{quantity}, #{price}, #{subtotal})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderItem orderItem);

    @Select("SELECT oi.*, p.name as product_name, p.description as product_description " +
            "FROM order_items oi " +
            "LEFT JOIN products p ON oi.product_id = p.id " +
            "WHERE oi.order_id = #{orderId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "product.name", column = "product_name"),
            @Result(property = "product.description", column = "product_description")
    })
    List<OrderItem> findByOrderId(Long orderId);

    @Select("SELECT * FROM order_items WHERE order_id = #{orderId}")
    List<OrderItem> findSimpleByOrderId(Long orderId);
}
