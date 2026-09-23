package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.DebitAccountCommand;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.repository.AccountRepository;

import java.util.Objects;

public final class DebitAccountService implements DebitAccountUseCase {

    private final AccountRepository accountRepository;

    public DebitAccountService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    }

    @Override
    public AccountResult execute(DebitAccountCommand command) {
        AccountId id = new AccountId(command.accountId());
        Account account = accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        account.debit(command.amount());
        return AccountResult.from(accountRepository.save(account));
    }
}
