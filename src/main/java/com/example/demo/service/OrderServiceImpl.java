package com.example.demo.service;


import com.example.demo.dto.PurchaseRequest;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.exception.InsufficientStockException;
import com.example.demo.mapper.OrderItemMapper;
import com.example.demo.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductService productService;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            ProductService productService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productService = productService;
    }

    @Override
    @Transactional
    public Order createOrder(PurchaseRequest purchaseRequest) {
        // 生成订单号
        String orderNumber = generateOrderNumber();

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUserId(purchaseRequest.getUserId());
        order.setStatus("PENDING");
        order.setTotalAmount(BigDecimal.ZERO);

        // 插入订单
        orderMapper.insert(order);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // 处理每个商品
        for (PurchaseRequest.PurchaseItem item : purchaseRequest.getItems()) {
            Long productId = item.getProductId();
            Integer quantity = item.getQuantity();

            // 检查库存
            Integer currentStock = productService.getProductStock(productId);
            if (currentStock == null || currentStock < quantity) {
                throw new InsufficientStockException("商品ID: " + productId + " 库存不足");
            }

            // 扣减库存
            if (!productService.deductStock(productId, quantity)) {
                throw new InsufficientStockException("商品ID: " + productId + " 库存扣减失败");
            }

            // 创建订单项
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(productId);
            orderItem.setQuantity(quantity);

            // 这里应该查询商品价格，简化处理
            BigDecimal price = BigDecimal.valueOf(100); // 实际应该从数据库获取
            orderItem.setPrice(price);

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
            orderItem.setSubtotal(subtotal);
            totalAmount = totalAmount.add(subtotal);

            orderItemMapper.insert(orderItem);
        }

        // 更新订单总金额
        order.setTotalAmount(totalAmount);
        // 实际应该有一个update方法来更新订单金额

        return order;
    }

    @Override
    public Optional<Order> getOrderById(Long orderId) {
        return orderMapper.findById(orderId);
    }

    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        return orderMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public boolean cancelOrder(Long orderId) {
        Optional<Order> orderOpt = orderMapper.findById(orderId);
        if (orderOpt.isEmpty()) {
            return false;
        }

        Order order = orderOpt.get();
        if (!"PENDING".equals(order.getStatus())) {
            return false;
        }

        // 恢复库存
        List<OrderItem> items = orderItemMapper.findSimpleByOrderId(orderId);
        for (OrderItem item : items) {
            productService.increaseStock(item.getProductId(), item.getQuantity());
        }

        // 更新订单状态为已取消
        orderMapper.updateStatus(orderId, "CANCELLED");
        return true;
    }

    private String generateOrderNumber() {
        return "ORD" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
