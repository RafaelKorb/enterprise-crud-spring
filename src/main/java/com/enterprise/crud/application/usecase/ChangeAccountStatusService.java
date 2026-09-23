package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.ChangeAccountStatusCommand;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.repository.AccountRepository;

import java.util.Objects;

public final class ChangeAccountStatusService implements ChangeAccountStatusUseCase {

    private final AccountRepository accountRepository;

    public ChangeAccountStatusService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    }

    @Override
    public AccountResult execute(ChangeAccountStatusCommand command) {
        AccountId id = new AccountId(command.accountId());
        Account account = accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        switch (command.targetStatus()) {
            case ACTIVE -> account.activate();
            case BLOCKED -> account.block();
            case CLOSED -> account.close();
        }
        return AccountResult.from(accountRepository.save(account));
    }
}
