package com.example.demo.mapper;


import com.example.demo.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO orders (order_number, user_id, total_amount, status) " +
            "VALUES (#{orderNumber}, #{userId}, #{totalAmount}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Select("SELECT * FROM orders WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "orderItems", column = "id",
                    many = @Many(select = "com.example.eshop.mapper.OrderItemMapper.findByOrderId"))
    })
    Optional<Order> findById(Long id);

    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY created_time DESC")
    List<Order> findByUserId(Long userId);

    @Update("UPDATE orders SET status = #{status} WHERE id = #{orderId}")
    int updateStatus(@Param("orderId") Long orderId, @Param("status") String status);
}
