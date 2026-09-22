package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.domain.exception.DocumentNumberAlreadyRegisteredException;
import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.DocumentNumber;
import com.enterprise.crud.domain.repository.AccountRepository;

import java.util.Objects;

public final class OpenAccountService implements OpenAccountUseCase {

    private final AccountRepository accountRepository;

    public OpenAccountService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    }

    /**
     * The existence check gives a clear error in the common case; a concurrent open for the same document is still
     * rejected by the unique constraint enforced in persistence.
     */
    @Override
    public AccountResult execute(OpenAccountCommand command) {
        DocumentNumber documentNumber = new DocumentNumber(command.documentNumber());
        if (accountRepository.existsByDocumentNumber(documentNumber)) {
            throw new DocumentNumberAlreadyRegisteredException(documentNumber);
        }
        return AccountResult.from(accountRepository.save(Account.open(documentNumber)));
    }
}
