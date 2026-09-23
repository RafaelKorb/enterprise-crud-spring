package com.enterprise.crud.application.dto;

import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResult(UUID id, String documentNumber, BigDecimal balance, AccountStatus status,
        Instant createdAt, Instant updatedAt, long version) {

    public static AccountResult from(Account account) {
        return new AccountResult(account.id().value(), account.documentNumber().value(), account.balance(),
                account.status(), account.createdAt(), account.updatedAt(), account.version());
    }
}
