package com.example.eshop.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(nullable = false)
    private String memberId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    public static Order create(String orderId, String memberId, Long productId,
                               int quantity, BigDecimal totalAmount) {
        Order o = new Order();
        o.orderId = orderId;
        o.memberId = memberId;
        o.productId = productId;
        o.quantity = quantity;
        o.totalAmount = totalAmount;
        o.status = OrderStatus.PENDING;
        o.createdAt = Instant.now();
        o.updatedAt = Instant.now();
        return o;
    }

    public void markInventoryChecking() {
        this.status = OrderStatus.INVENTORY_CHECKING;
        this.updatedAt = Instant.now();
    }

    public void markPaymentProcessing() {
        this.status = OrderStatus.PAYMENT_PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }
}
