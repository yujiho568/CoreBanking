package com.example.eshop.order.domain;

public enum OrderStatus {
    PENDING,            // 주문 생성됨, Saga 시작 전
    INVENTORY_CHECKING, // 재고 확인 중
    PAYMENT_PROCESSING, // 결제 처리 중
    COMPLETED,          // 주문 완료
    CANCELLED           // 주문 취소 (보상 트랜잭션 완료)
}
