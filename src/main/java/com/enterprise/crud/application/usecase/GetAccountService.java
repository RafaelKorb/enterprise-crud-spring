package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.GetAccountQuery;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.repository.AccountRepository;

import java.util.Objects;

public final class GetAccountService implements GetAccountUseCase {

    private final AccountRepository accountRepository;

    public GetAccountService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    }

    @Override
    public AccountResult execute(GetAccountQuery query) {
        AccountId id = new AccountId(query.accountId());
        return accountRepository.findById(id)
                .map(AccountResult::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
