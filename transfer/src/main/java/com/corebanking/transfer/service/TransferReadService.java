package com.corebanking.transfer.service;

import com.corebanking.transfer.entity.Transfer;
import com.corebanking.transfer.exception.TransferNotFoundException;
import com.corebanking.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferReadService {

    private final TransferRepository transferRepository;

    public TransferReadService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(String transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
