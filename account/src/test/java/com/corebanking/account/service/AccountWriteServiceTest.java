package com.corebanking.account.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AccountWriteServiceTest {
    @Test void optimisticModeKeepsVersionBasedReadsWithoutPessimisticQueries() {
        var repository = mock(AccountRepository.class);
        var properties = new AccountLockProperties();
        properties.setLockMode(AccountLockMode.OPTIMISTIC);
        when(repository.findById("A")).thenReturn(Optional.of(Account.open("A", "a", BigDecimal.TEN)));
        when(repository.findById("B")).thenReturn(Optional.of(Account.open("B", "b", BigDecimal.TEN)));
        new AccountWriteService(repository, properties).lockTransferAccounts("B", "A");
        verify(repository).findById("A"); verify(repository).findById("B");
        verify(repository, never()).findByIdForUpdate(any());
    }

    @Test void bothDirectionsAcquireFirstLocksInTheSameOrderBeforeReservation() {
        for (boolean reverse : new boolean[]{false, true}) {
            var repository = mock(AccountRepository.class);
            var properties = new AccountLockProperties();
            properties.setLockMode(AccountLockMode.PESSIMISTIC);
            var a = Account.open("A", "a", new BigDecimal("100"));
            var b = Account.open("B", "b", new BigDecimal("100"));
            when(repository.findByIdForUpdate("A")).thenReturn(Optional.of(a));
            when(repository.findByIdForUpdate("B")).thenReturn(Optional.of(b));
            var service = new AccountWriteService(repository, properties);
            String from = reverse ? "B" : "A", to = reverse ? "A" : "B";
            service.lockTransferAccounts(from, to);
            var order = inOrder(repository);
            order.verify(repository).findByIdForUpdate("A");
            order.verify(repository).findByIdForUpdate("B");
            service.reserve(from, BigDecimal.TEN);
            service.commitTransfer(from, to, BigDecimal.TEN);

            assertThat(a.getBalance().add(b.getBalance())).isEqualByComparingTo("200");
            assertThat(a.getReservedBalance().add(b.getReservedBalance())).isZero();
        }
    }
}
