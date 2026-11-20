package com.corebanking.openbanking.infrastructure;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.corebanking.openbanking.config.OpenBankingProperties;
import com.corebanking.openbanking.exception.OpenBankingProviderException;
import com.corebanking.openbanking.exception.OpenBankingReauthorizationRequiredException;
import com.corebanking.openbanking.port.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
public class KftcOpenBankingAdapter implements OpenBankingPort {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final OpenBankingProperties properties;
    private final RestClient client;

    public KftcOpenBankingAdapter(OpenBankingProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public URI authorizationUri(String state) {
        properties.requireClientCredentials();
        return UriComponentsBuilder.fromUri(properties.authorizationUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scopes())
                .queryParam("state", state)
                .queryParam("auth_type", properties.authType())
                .build()
                .encode()
                .toUri();
    }

    @Override
    public TokenBundle exchangeAuthorizationCode(String code) {
        properties.requireClientCredentials();
        LinkedMultiValueMap<String, String> form = baseTokenForm();
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri().toString());
        form.add("grant_type", "authorization_code");
        return requestToken(form, null);
    }

    @Override
    public TokenBundle refreshToken(String refreshToken) {
        properties.requireClientCredentials();
        LinkedMultiValueMap<String, String> form = baseTokenForm();
        form.add("refresh_token", refreshToken);
        form.add("scope", properties.scopes());
        form.add("grant_type", "refresh_token");
        return requestToken(form, refreshToken);
    }

    @Override
    public UserAccounts getUserAccounts(String accessToken, String userSeqNo) {
        UserInfoResponse response = get("/v2.0/user/me", accessToken,
                builder -> builder.queryParam("user_seq_no", userSeqNo), UserInfoResponse.class);
        requireSuccess(response.rspCode(), response.rspMessage());
        List<RegisteredExternalAccount> accounts = response.accounts() == null ? List.of() : response.accounts().stream()
                .map(account -> new RegisteredExternalAccount(
                        account.fintechUseNum(), account.bankCode(), account.bankName(), account.accountAlias(),
                        account.maskedAccountNumber(), "Y".equalsIgnoreCase(account.inquiryAgreeYn())))
                .toList();
        return new UserAccounts(response.userSeqNo(), accounts);
    }

    @Override
    public BalanceInfo getBalance(String accessToken, String fintechUseNum) {
        properties.requireInquiryConfiguration();
        BalanceResponse response = get("/v2.0/account/balance/fin_num", accessToken,
                builder -> builder
                        .queryParam("bank_tran_id", newBankTransactionId())
                        .queryParam("fintech_use_num", fintechUseNum)
                        .queryParam("tran_dtime", now()), BalanceResponse.class);
        requireSuccess(response.rspCode(), response.rspMessage());
        requireBankSuccess(response.bankRspCode(), response.bankRspMessage());
        return new BalanceInfo(response.apiTranId(), response.bankName(), money(response.balanceAmount()),
                money(response.availableAmount()), response.accountType(), response.productName(),
                response.lastTransactionDate(), Instant.now());
    }

    @Override
    public TransactionPage getTransactions(String accessToken, String fintechUseNum, TransactionQuery query) {
        properties.requireInquiryConfiguration();
        TransactionResponse response = get("/v2.0/account/transaction_list/fin_num", accessToken,
                builder -> {
                    builder.queryParam("bank_tran_id", newBankTransactionId())
                            .queryParam("fintech_use_num", fintechUseNum)
                            .queryParam("inquiry_type", query.inquiryType())
                            .queryParam("inquiry_base", "D")
                            .queryParam("from_date", DATE.format(query.fromDate()))
                            .queryParam("from_time", "000000")
                            .queryParam("to_date", DATE.format(query.toDate()))
                            .queryParam("to_time", "235959")
                            .queryParam("sort_order", query.sortOrder())
                            .queryParam("tran_dtime", now());
                    if (query.beforeInquiryTraceInfo() != null && !query.beforeInquiryTraceInfo().isBlank()) {
                        builder.queryParam("befor_inquiry_trace_info", query.beforeInquiryTraceInfo());
                    }
                    return builder;
                }, TransactionResponse.class);
        requireSuccess(response.rspCode(), response.rspMessage());
        requireBankSuccess(response.bankRspCode(), response.bankRspMessage());
        List<ExternalTransactionInfo> transactions = response.transactions() == null ? List.of() :
                response.transactions().stream().map(this::toTransaction).toList();
        return new TransactionPage(response.apiTranId(), transactions,
                "Y".equalsIgnoreCase(response.nextPageYn()), response.beforeInquiryTraceInfo());
    }

    private TokenBundle requestToken(LinkedMultiValueMap<String, String> form, String fallbackRefreshToken) {
        try {
            TokenResponse response = client.post()
                    .uri("/oauth/2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new OpenBankingProviderException("Open Banking returned an empty token response.");
            }
            long expiresIn = response.expiresIn() == null ? 0 : response.expiresIn();
            return new TokenBundle(response.accessToken(),
                    response.refreshToken() == null ? fallbackRefreshToken : response.refreshToken(),
                    Instant.now().plusSeconds(expiresIn), response.scope(), response.userSeqNo());
        } catch (ResourceAccessException failure) {
            throw OpenBankingProviderException.timeout();
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 400 || failure.getStatusCode().value() == 401) {
                throw new OpenBankingReauthorizationRequiredException();
            }
            throw providerHttpFailure(failure);
        }
    }

    private <T> T get(String path, String accessToken,
                      java.util.function.UnaryOperator<UriComponentsBuilder> query, Class<T> responseType) {
        try {
            URI uri = query.apply(UriComponentsBuilder.fromUri(properties.baseUrl()).path(path)).build(true).toUri();
            T body = client.get().uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(responseType);
            if (body == null) throw new OpenBankingProviderException("Open Banking returned an empty response.");
            return body;
        } catch (ResourceAccessException failure) {
            throw OpenBankingProviderException.timeout();
        } catch (RestClientResponseException failure) {
            throw providerHttpFailure(failure);
        }
    }

    private LinkedMultiValueMap<String, String> baseTokenForm() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private ExternalTransactionInfo toTransaction(TransactionItem item) {
        LocalDateTime occurredAt = LocalDateTime.parse(item.transactionDate() + item.transactionTime(), DATE_TIME);
        return new ExternalTransactionInfo(occurredAt, item.inOutType(), item.transactionType(),
                item.description(), money(item.amount()), money(item.balanceAfter()), item.branchName());
    }

    private String newBankTransactionId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        return properties.clientUseCode() + "U" + suffix;
    }

    private String now() {
        return DATE_TIME.format(LocalDateTime.now(SEOUL));
    }

    private BigDecimal money(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private void requireSuccess(String code, String message) {
        if (!"A0000".equals(code)) {
            throw new OpenBankingProviderException("Open Banking rejected the request: " + code + " " + safe(message));
        }
    }

    private void requireBankSuccess(String code, String message) {
        if (code != null && !"000".equals(code)) {
            throw new OpenBankingProviderException("The bank rejected the request: " + code + " " + safe(message));
        }
    }

    private OpenBankingProviderException providerHttpFailure(RestClientResponseException failure) {
        return new OpenBankingProviderException("Open Banking HTTP error: " + failure.getStatusCode().value());
    }

    private String safe(String value) { return value == null ? "" : value; }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            String scope,
            @JsonProperty("user_seq_no") String userSeqNo) {}

    private record UserInfoResponse(
            @JsonProperty("rsp_code") String rspCode,
            @JsonProperty("rsp_message") String rspMessage,
            @JsonProperty("user_seq_no") String userSeqNo,
            @JsonProperty("res_list") List<UserAccountItem> accounts) {}

    private record UserAccountItem(
            @JsonProperty("fintech_use_num") String fintechUseNum,
            @JsonProperty("bank_code_std") String bankCode,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_alias") String accountAlias,
            @JsonProperty("account_num_masked") String maskedAccountNumber,
            @JsonProperty("inquiry_agree_yn") String inquiryAgreeYn) {}

    private record BalanceResponse(
            @JsonProperty("api_tran_id") String apiTranId,
            @JsonProperty("rsp_code") String rspCode,
            @JsonProperty("rsp_message") String rspMessage,
            @JsonProperty("bank_rsp_code") String bankRspCode,
            @JsonProperty("bank_rsp_message") String bankRspMessage,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("balance_amt") String balanceAmount,
            @JsonProperty("available_amt") String availableAmount,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("product_name") String productName,
            @JsonProperty("last_tran_date") String lastTransactionDate) {}

    private record TransactionResponse(
            @JsonProperty("api_tran_id") String apiTranId,
            @JsonProperty("rsp_code") String rspCode,
            @JsonProperty("rsp_message") String rspMessage,
            @JsonProperty("bank_rsp_code") String bankRspCode,
            @JsonProperty("bank_rsp_message") String bankRspMessage,
            @JsonProperty("next_page_yn") String nextPageYn,
            @JsonProperty("befor_inquiry_trace_info") String beforeInquiryTraceInfo,
            @JsonProperty("res_list") List<TransactionItem> transactions) {}

    private record TransactionItem(
            @JsonProperty("tran_date") String transactionDate,
            @JsonProperty("tran_time") String transactionTime,
            @JsonProperty("inout_type") String inOutType,
            @JsonProperty("tran_type") String transactionType,
            @JsonAlias({"print_content", "printed_content"}) String description,
            @JsonProperty("tran_amt") String amount,
            @JsonProperty("after_balance_amt") String balanceAfter,
            @JsonProperty("branch_name") String branchName) {}
}
