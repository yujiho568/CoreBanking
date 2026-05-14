package com.corebanking.ledger.service;

import com.corebanking.common.event.AccountReservedEvent;
import com.corebanking.common.event.LedgerFailedEvent;
import com.corebanking.common.event.LedgerRecordedEvent;
import com.corebanking.ledger.domain.LedgerEntry;
import com.corebanking.ledger.domain.LedgerEntryRepository;
import com.corebanking.ledger.domain.ProcessedEvent;
import com.corebanking.ledger.domain.ProcessedEventRepository;
import com.corebanking.ledger.kafka.LedgerEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerEventPublisher publisher;

    @Transactional
    public void recordDoubleEntry(AccountReservedEvent event) {
        // 멱등성 체크 — 같은 eventId가 두 번 오면 이미 기록된 것
        if (processedEventRepository.existsById(event.eventId())) {
            log.warn("duplicate_event skipped eventId={} transferId={}", event.eventId(), event.transferId());
            return;
        }

        try {
            // 이중장부: 차변(출금) + 대변(입금) 동시 기록
            LedgerEntry debit  = LedgerEntry.debit(event.transferId(), event.fromAccountId(), event.amount());
            LedgerEntry credit = LedgerEntry.credit(event.transferId(), event.toAccountId(), event.amount());
            ledgerEntryRepository.save(debit);
            ledgerEntryRepository.save(credit);

            // 처리 완료 기록
            processedEventRepository.save(ProcessedEvent.of(event.eventId()));

            log.info("ledger_recorded transferId={} debit={} credit={} amount={}",
                    event.transferId(), event.fromAccountId(), event.toAccountId(), event.amount());

            publisher.publishLedgerRecorded(LedgerRecordedEvent.of(event.transferId(), event.amount()));

        } catch (Exception e) {
            log.error("ledger_failed transferId={} reason={}", event.transferId(), e.getMessage());
            publisher.publishLedgerFailed(LedgerFailedEvent.of(event.transferId(), "원장 기록 실패: " + e.getMessage()));
        }
    }
}
