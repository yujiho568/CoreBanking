package com.corebanking.openbanking.port;

import java.util.List;

public record UserAccounts(String userSeqNo, List<RegisteredExternalAccount> accounts) {
}
