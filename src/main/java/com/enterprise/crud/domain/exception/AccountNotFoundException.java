package com.enterprise.crud.domain.exception;

import com.enterprise.crud.domain.model.AccountId;

public final class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(AccountId id) {
        super("Account not found: " + id);
    }
}
