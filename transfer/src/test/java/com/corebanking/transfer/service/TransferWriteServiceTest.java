package com.corebanking.transfer.service;

import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.Transfer;
import com.corebanking.transfer.exception.IdempotencyConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class TransferWriteServiceTest {
    private CreateTransferRequest request(String to, String amount) {
        return new CreateTransferRequest("key", "A", to, new BigDecimal(amount));
    }
    @Test void sameNumericAmountReplaysButDifferentContentsConflict() {
        var transactions = new TransferTransactionService(null, null, null, null, null);
        var transfer = Transfer.requested("id", "key", "A", "B", new BigDecimal("10.00"));
        transfer.complete();
        assertThat(transactions.validate(transfer, request("B", "10.0"))).isSameAs(transfer);
        assertThatThrownBy(() -> transactions.validate(transfer, request("C", "10"))).isInstanceOf(IdempotencyConflictException.class);
        assertThatThrownBy(() -> transactions.validate(transfer, request("B", "11"))).isInstanceOf(IdempotencyConflictException.class);
    }
    @Test void onlyNamedUniqueConstraintTriggersRecovery() {
        var transactions = mock(TransferTransactionService.class);
        var request = request("B", "10");
        var collision = new DataIntegrityViolationException("duplicate", new ConstraintViolationException("duplicate", new SQLException(), "transfers.uk_transfers_idempotency_key"));
        when(transactions.execute(request)).thenThrow(collision);
        new TransferWriteService(transactions).execute(request);
        verify(transactions).recover(request);
        reset(transactions);
        var unrelated = new DataIntegrityViolationException("other", new ConstraintViolationException("other", new SQLException(), "other_constraint"));
        when(transactions.execute(request)).thenThrow(unrelated);
        assertThatThrownBy(() -> new TransferWriteService(transactions).execute(request)).isSameAs(unrelated);
        verify(transactions, never()).recover(any());
    }
}
