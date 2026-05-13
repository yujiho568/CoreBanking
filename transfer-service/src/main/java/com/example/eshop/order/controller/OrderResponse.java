package com.example.eshop.order.controller;

import com.example.eshop.order.domain.Order;
import com.example.eshop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderId,
        String memberId,
        Long productId,
        int quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        String failureReason,
        Instant createdAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getOrderId(), o.getMemberId(), o.getProductId(),
                o.getQuantity(), o.getTotalAmount(), o.getStatus(),
                o.getFailureReason(), o.getCreatedAt()
        );
    }
}
