package com.corebanking.account.service;

import com.corebanking.account.dto.AccountTransferResult;
import com.corebanking.account.entity.Account;
import com.corebanking.account.entity.AccountStatus;
import com.corebanking.account.exception.AccountNotFoundException;
import com.corebanking.account.exception.InsufficientBalanceException;
import com.corebanking.account.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AccountWriteService {

    // DB?먯꽌 怨꾩쥖瑜?議고쉶?섎뒗 Repository
    private final AccountRepository accountRepository;
    // ?ㅼ젙媛믪뿉 ?곕씪 ?숆??????먮뒗 鍮꾧?????議고쉶 諛⑹떇???좏깮?쒕떎.
    private final AccountLockProperties lockProperties;

    public AccountWriteService(AccountRepository accountRepository, AccountLockProperties lockProperties) {
        this.accountRepository = accountRepository;
        this.lockProperties = lockProperties;
    }

    // ?↔툑 ?꾩뿉 異쒓툑 怨꾩쥖???ъ슜 媛??湲덉븸???덉빟?쒕떎.
    public void reserve(String accountId, BigDecimal amount) {
        Account account = getRequiredAccount(accountId);
        validateActive(account);
        validatePositive(amount);

        BigDecimal availableBalance = calculateAvailableBalance(account);
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(accountId, availableBalance, amount);
        }

        account.changeReservedBalance(account.getReservedBalance().add(amount));
    }

    // ?덉빟??異쒓툑 湲덉븸???ㅼ젣 ?붿븸?먯꽌 李④컧?섍퀬, ?낃툑 怨꾩쥖??湲덉븸???뷀븳??
    public AccountTransferResult commitTransfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        Account from = getRequiredAccount(fromAccountId);
        Account to = getRequiredAccount(toAccountId);

        validateActive(from);
        validateActive(to);
        validatePositive(amount);

        if (from.getReservedBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Reserved balance is smaller than debit amount.");
        }

        from.changeReservedBalance(from.getReservedBalance().subtract(amount));
        from.changeBalance(from.getBalance().subtract(amount));
        to.changeBalance(to.getBalance().add(amount));

        return new AccountTransferResult(from.getBalance(), to.getBalance());
    }

    // 怨꾩쥖 ?묐떟 DTO?먯꽌 ?ъ슜?????덈룄濡??ъ슜 媛???붿븸??怨꾩궛?쒕떎.
    public BigDecimal calculateAvailableBalance(Account account) {
        return account.getBalance().subtract(account.getReservedBalance());
    }

    // 怨꾩쥖媛 諛섎뱶???덉뼱???섎뒗 寃쎌슦???ъ슜?쒕떎. ?놁쑝硫??꾨찓???덉쇅瑜??섏쭊??
    private Account getRequiredAccount(String accountId) {
        return findByConfiguredLockMode(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    // ?ㅼ젙????紐⑤뱶???곕씪 ?쇰컲 議고쉶 ?먮뒗 SELECT FOR UPDATE 議고쉶瑜??좏깮?쒕떎.
    private Optional<Account> findByConfiguredLockMode(String accountId) {
        if (lockProperties.getLockMode() == AccountLockMode.PESSIMISTIC) {
            return accountRepository.findByIdForUpdate(accountId);
        }
        return accountRepository.findById(accountId);
    }

    // ?ロ엺 怨꾩쥖?먯꽌???↔툑 愿???붿븸 蹂寃쎌쓣 ?????녿떎.
    private void validateActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getAccountId());
        }
    }

    // ?↔툑 湲덉븸? null???꾨땲怨?0蹂대떎 而ㅼ빞 ?쒕떎.
    private void validatePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
