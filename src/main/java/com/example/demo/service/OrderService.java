package com.example.demo.service;


import com.example.demo.dto.PurchaseRequest;
import com.example.demo.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderService {
    Order createOrder(PurchaseRequest purchaseRequest);

    Optional<Order> getOrderById(Long orderId);

    List<Order> getOrdersByUserId(Long userId);

    boolean cancelOrder(Long orderId);
}
