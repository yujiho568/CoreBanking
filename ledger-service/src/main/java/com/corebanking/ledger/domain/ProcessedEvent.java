package com.corebanking.ledger.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 멱등성 보장용 — 처리 완료된 eventId 기록
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public static ProcessedEvent of(String eventId) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.eventId = eventId;
        pe.processedAt = Instant.now();
        return pe;
    }
}
