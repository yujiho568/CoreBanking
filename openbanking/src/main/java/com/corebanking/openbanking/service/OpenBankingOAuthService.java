package com.corebanking.openbanking.service;

import com.corebanking.openbanking.dto.ExternalAccountResponse;
import com.corebanking.openbanking.dto.OpenBankingConnectionResponse;
import com.corebanking.openbanking.entity.ExternalAccount;
import com.corebanking.openbanking.entity.OAuthState;
import com.corebanking.openbanking.entity.OpenBankingConnection;
import com.corebanking.openbanking.port.*;
import com.corebanking.openbanking.repository.ExternalAccountRepository;
import com.corebanking.openbanking.repository.OAuthStateRepository;
import com.corebanking.openbanking.repository.OpenBankingConnectionRepository;
import com.corebanking.openbanking.security.SensitiveValueCipher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OpenBankingOAuthService {
    private final OpenBankingPort port;
    private final OAuthStateRepository states;
    private final OAuthStateService stateService;
    private final OpenBankingConnectionRepository connections;
    private final ExternalAccountRepository accounts;
    private final SensitiveValueCipher cipher;

    public OpenBankingOAuthService(OpenBankingPort port, OAuthStateRepository states,
                                   OAuthStateService stateService, OpenBankingConnectionRepository connections,
                                   ExternalAccountRepository accounts, SensitiveValueCipher cipher) {
        this.port = port;
        this.states = states;
        this.stateService = stateService;
        this.connections = connections;
        this.accounts = accounts;
        this.cipher = cipher;
    }

    public URI begin(String ownerId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        states.save(new OAuthState(state, ownerId, Instant.now().plus(10, ChronoUnit.MINUTES)));
        return port.authorizationUri(state);
    }

    public OpenBankingConnectionResponse complete(String code, String state) {
        String ownerId = stateService.consume(state);
        TokenBundle tokens = port.exchangeAuthorizationCode(code);
        if (tokens.userSeqNo() == null || tokens.userSeqNo().isBlank()) {
            throw new IllegalStateException("Open Banking token response did not include user_seq_no.");
        }
        OpenBankingConnection connection = connections.findByOwnerIdAndUserSeqNo(ownerId, tokens.userSeqNo())
                .orElseGet(() -> OpenBankingConnection.connect(ownerId, tokens.userSeqNo(),
                        cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                        tokens.expiresAt(), tokens.scope()));
        connection.updateTokens(cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                tokens.expiresAt(), tokens.scope());
        connection = connections.save(connection);

        UserAccounts registered = port.getUserAccounts(tokens.accessToken(), tokens.userSeqNo());
        List<ExternalAccountResponse> synchronizedAccounts = new ArrayList<>();
        String connectionId = connection.getConnectionId();
        for (RegisteredExternalAccount registeredAccount : registered.accounts()) {
            String fingerprint = cipher.fingerprint(registeredAccount.fintechUseNum());
            ExternalAccount account = accounts.findByConnectionIdAndFintechUseNumHash(connectionId, fingerprint)
                    .orElseGet(() -> ExternalAccount.connect(connectionId, ownerId,
                            cipher.encrypt(registeredAccount.fintechUseNum()), fingerprint,
                            registeredAccount.bankCode(), registeredAccount.bankName(),
                            registeredAccount.accountAlias(), registeredAccount.maskedAccountNumber(),
                            registeredAccount.inquiryAgreed()));
            account.updateMetadata(registeredAccount.bankCode(), registeredAccount.bankName(),
                    registeredAccount.accountAlias(), registeredAccount.maskedAccountNumber(),
                    registeredAccount.inquiryAgreed());
            synchronizedAccounts.add(ExternalAccountResponse.from(accounts.save(account)));
        }
        return new OpenBankingConnectionResponse(connection.getConnectionId(), connection.getUserSeqNo(),
                List.copyOf(synchronizedAccounts));
    }
}
