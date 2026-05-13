package com.example.eshop.order.service;

import com.example.eshop.common.event.*;
import com.example.eshop.order.controller.CreateOrderRequest;
import com.example.eshop.order.domain.Order;
import com.example.eshop.order.domain.OrderRepository;
import com.example.eshop.order.kafka.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher publisher;

    /**
     * Saga 시작 - 주문 생성 후 order.created 발행
     */
    @Transactional
    public Order createOrder(CreateOrderRequest req) {
        String orderId = UUID.randomUUID().toString();
        Order order = Order.create(orderId, req.memberId(), req.productId(),
                req.quantity(), req.totalAmount());
        order.markInventoryChecking();
        orderRepository.save(order);

        OrderCreatedEvent event = OrderCreatedEvent.of(
                orderId, req.memberId(), req.productId(),
                req.quantity(), req.totalAmount()
        );
        publisher.publishOrderCreated(event);
        log.info("order_created orderId={} memberId={}", orderId, req.memberId());
        return order;
    }

    /**
     * 재고 예약 완료 → 결제 처리 단계로 전환
     */
    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.markPaymentProcessing();
            log.info("order_payment_processing orderId={}", event.orderId());
        });
    }

    /**
     * 재고 부족 → 주문 취소 (보상 트랜잭션 불필요 - 재고 변경 없음)
     */
    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.cancel(event.reason());
            log.warn("order_cancelled_by_inventory orderId={} reason={}", event.orderId(), event.reason());

            OrderCancelledEvent cancelled = OrderCancelledEvent.of(
                    event.orderId(), event.productId(), event.requestedQuantity(), event.reason()
            );
            publisher.publishOrderCancelled(cancelled);
        });
    }

    /**
     * 결제 완료 → 주문 완료
     */
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.complete();
            log.info("order_completed orderId={}", event.orderId());

            OrderCompletedEvent completed = OrderCompletedEvent.of(
                    event.orderId(), order.getMemberId(), event.amount()
            );
            publisher.publishOrderCompleted(completed);
        });
    }

    /**
     * 결제 실패 → 주문 취소 + order.cancelled 발행 (재고 보상 트랜잭션 트리거)
     */
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.cancel(event.reason());
            log.warn("order_cancelled_by_payment orderId={} reason={}", event.orderId(), event.reason());

            OrderCancelledEvent cancelled = OrderCancelledEvent.of(
                    event.orderId(), order.getProductId(), order.getQuantity(), event.reason()
            );
            publisher.publishOrderCancelled(cancelled);
        });
    }

    @Transactional(readOnly = true)
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
