package com.corebanking.account.service;

import com.corebanking.account.domain.Account;
import com.corebanking.account.domain.AccountRepository;
import com.corebanking.account.domain.InsufficientBalanceException;
import com.corebanking.account.kafka.AccountEventPublisher;
import com.corebanking.common.event.AccountFailedEvent;
import com.corebanking.common.event.AccountReservedEvent;
import com.corebanking.common.event.TransferCancelledEvent;
import com.corebanking.common.event.TransferCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountEventPublisher publisher;

    @Transactional
    public void reserveBalance(TransferCreatedEvent event) {
        Account account = accountRepository.findById(event.fromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("계좌 없음: " + event.fromAccountId()));
        try {
            account.reserve(event.amount());
            log.info("balance_reserved transferId={} accountId={} amount={}",
                    event.transferId(), event.fromAccountId(), event.amount());
            publisher.publishAccountReserved(
                    AccountReservedEvent.of(event.transferId(), event.fromAccountId(),
                            event.toAccountId(), event.amount())
            );
        } catch (InsufficientBalanceException e) {
            log.warn("insufficient_balance transferId={} accountId={}", event.transferId(), event.fromAccountId());
            publisher.publishAccountFailed(
                    AccountFailedEvent.of(event.transferId(), event.fromAccountId(), "잔액 부족")
            );
        } catch (ObjectOptimisticLockingFailureException e) {
            // 동시 이체 요청이 같은 계좌를 수정하려 할 때 발생
            log.warn("optimistic_lock_failure transferId={} accountId={}", event.transferId(), event.fromAccountId());
            publisher.publishAccountFailed(
                    AccountFailedEvent.of(event.transferId(), event.fromAccountId(), "동시성 충돌, 재시도 필요")
            );
        }
    }

    // 원장 기록 실패 시 보상 트랜잭션 — 차감했던 잔액 복원
    @Transactional
    public void rollbackBalance(TransferCancelledEvent event) {
        if (event.reservedAmount() == null) {
            // 잔액 부족으로 취소된 경우: 차감 자체가 없었으므로 롤백 불필요
            return;
        }
        accountRepository.findById(event.fromAccountId()).ifPresent(account -> {
            account.restore(event.reservedAmount());
            log.info("balance_restored transferId={} accountId={} amount={}",
                    event.transferId(), event.fromAccountId(), event.reservedAmount());
        });
    }
}
